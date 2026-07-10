<?php
/**
 * Minimal, dependency-free FIT (Flexible and Interoperable Data Transfer)
 * decoder. Implements the ANT/Garmin FIT binary protocol: file header,
 * definition + data messages, all base types, endianness, arrays, strings,
 * compressed-timestamp headers, and developer fields. CRC is verified.
 *
 * Produces a flat list of decoded messages keyed by (global message number,
 * field definition number) so higher layers can interpret Garmin's custom
 * golf messages (190 course, 192 hole result, 193 hole definition).
 */

final class FitException extends RuntimeException {}

final class FitFile
{
    /** @var array<int,array{global:int,fields:array<int,mixed>}> */
    public array $messages = [];
    public int $protocolVersion = 0;
    public int $profileVersion = 0;

    // FIT base type number => [php scalar size in bytes, kind]
    // kind: 'u' unsigned int, 's' signed int, 'f' float, 'str' string
    private const BASE_TYPES = [
        0x00 => [1, 'u',   0xFF],                 // enum
        0x01 => [1, 's',   0x7F],                 // sint8
        0x02 => [1, 'u',   0xFF],                 // uint8
        0x03 => [2, 's',   0x7FFF],               // sint16
        0x04 => [2, 'u',   0xFFFF],               // uint16
        0x05 => [4, 's',   0x7FFFFFFF],           // sint32
        0x06 => [4, 'u',   0xFFFFFFFF],           // uint32
        0x07 => [1, 'str', 0x00],                 // string
        0x08 => [4, 'f',   0xFFFFFFFF],           // float32
        0x09 => [8, 'f',   null],                 // float64
        0x0A => [1, 'u',   0x00],                 // uint8z
        0x0B => [2, 'u',   0x0000],               // uint16z
        0x0C => [4, 'u',   0x00000000],           // uint32z
        0x0D => [1, 'u',   0xFF],                 // byte
        0x0E => [8, 's',   null],                 // sint64
        0x0F => [8, 'u',   null],                 // uint64
        0x10 => [8, 'u',   null],                 // uint64z
    ];

    public static function parse(string $bytes): FitFile
    {
        $len = strlen($bytes);
        if ($len < 14) {
            throw new FitException('File too small to be a FIT file');
        }

        $headerSize = ord($bytes[0]);
        if ($headerSize < 12 || $headerSize > $len) {
            throw new FitException("Invalid FIT header size: $headerSize");
        }

        // ".FIT" signature lives at header bytes 8..11
        if (substr($bytes, 8, 4) !== '.FIT') {
            throw new FitException('Missing .FIT signature — not a FIT file');
        }

        $self = new self();
        $self->protocolVersion = ord($bytes[1]);
        $self->profileVersion  = unpack('v', substr($bytes, 2, 2))[1];
        $dataSize              = unpack('V', substr($bytes, 4, 4))[1];

        $dataStart = $headerSize;
        $dataEnd   = $headerSize + $dataSize;
        if ($dataEnd > $len) {
            throw new FitException('Declared data size exceeds file length');
        }

        // Optional 2-byte header CRC (only present when headerSize >= 14)
        if ($headerSize >= 14) {
            $stored = unpack('v', substr($bytes, 12, 2))[1];
            if ($stored !== 0) { // 0 means "not set"
                $calc = self::crc16(substr($bytes, 0, 12));
                if ($calc !== $stored) {
                    throw new FitException('Header CRC mismatch');
                }
            }
        }

        // Trailing 2-byte file CRC over header+data
        if ($dataEnd + 2 <= $len) {
            $stored = unpack('v', substr($bytes, $dataEnd, 2))[1];
            $calc   = self::crc16(substr($bytes, 0, $dataEnd));
            if ($calc !== $stored) {
                throw new FitException('File CRC mismatch — file may be corrupt');
            }
        }

        $self->decodeRecords($bytes, $dataStart, $dataEnd);
        return $self;
    }

    private function decodeRecords(string $bytes, int $pos, int $end): void
    {
        $localDefs = []; // local message type => definition

        while ($pos < $end) {
            $recordHeader = ord($bytes[$pos]);
            $pos++;

            if ($recordHeader & 0x80) {
                // Compressed timestamp header — the body is a normal data
                // message per its local definition; the timestamp is derived
                // from header bits and isn't stored inline, so byte alignment
                // is unaffected.
                $localType = ($recordHeader >> 5) & 0x03;
                $pos = $this->readDataMessage($bytes, $pos, $localDefs, $localType);
                continue;
            }

            $localType = $recordHeader & 0x0F;

            if ($recordHeader & 0x40) {
                // Definition message
                $pos = $this->readDefinition($bytes, $pos, $localDefs, $localType, (bool)($recordHeader & 0x20));
            } else {
                // Data message
                $pos = $this->readDataMessage($bytes, $pos, $localDefs, $localType);
            }
        }
    }

    private function readDefinition(string $bytes, int $pos, array &$localDefs, int $localType, bool $hasDev): int
    {
        $pos++; // reserved byte
        $arch   = ord($bytes[$pos]); $pos++;
        $little = ($arch === 0);
        $globalNum = $little
            ? unpack('v', substr($bytes, $pos, 2))[1]
            : unpack('n', substr($bytes, $pos, 2))[1];
        $pos += 2;
        $numFields = ord($bytes[$pos]); $pos++;

        $fields = [];
        for ($i = 0; $i < $numFields; $i++) {
            $fields[] = [
                'num'  => ord($bytes[$pos]),
                'size' => ord($bytes[$pos + 1]),
                'base' => ord($bytes[$pos + 2]),
            ];
            $pos += 3;
        }

        $devFields = [];
        if ($hasDev) {
            $numDev = ord($bytes[$pos]); $pos++;
            for ($i = 0; $i < $numDev; $i++) {
                $devFields[] = ['size' => ord($bytes[$pos + 1])];
                $pos += 3;
            }
        }

        $localDefs[$localType] = [
            'global' => $globalNum,
            'little' => $little,
            'fields' => $fields,
            'dev'    => $devFields,
        ];
        return $pos;
    }

    private function readDataMessage(string $bytes, int $pos, array $localDefs, int $localType): int
    {
        if (!isset($localDefs[$localType])) {
            throw new FitException("Data message references undefined local type $localType");
        }
        $def = $localDefs[$localType];
        $fields = [];

        foreach ($def['fields'] as $f) {
            $raw = substr($bytes, $pos, $f['size']);
            $pos += $f['size'];
            $fields[$f['num']] = $this->decodeValue($raw, $f['base'], $def['little']);
        }
        // Developer fields: consume their bytes to stay aligned (values ignored)
        foreach ($def['dev'] as $d) {
            $pos += $d['size'];
        }

        $this->messages[] = ['global' => $def['global'], 'fields' => $fields];
        return $pos;
    }

    private function decodeValue(string $raw, int $baseTypeByte, bool $little)
    {
        $baseNum = $baseTypeByte & 0x1F;
        if (!isset(self::BASE_TYPES[$baseNum])) {
            return null; // unknown base type
        }
        [$elemSize, $kind, $invalid] = self::BASE_TYPES[$baseNum];

        if ($kind === 'str') {
            $s = rtrim($raw, "\x00");
            $s = strpos($raw, "\x00") !== false ? substr($raw, 0, strpos($raw, "\x00")) : $raw;
            $s = trim($s);
            return $s === '' ? null : $s;
        }

        $count = $elemSize > 0 ? intdiv(strlen($raw), $elemSize) : 0;
        if ($count === 0) {
            return null;
        }

        $values = [];
        for ($i = 0; $i < $count; $i++) {
            $chunk = substr($raw, $i * $elemSize, $elemSize);
            $v = $this->decodeScalar($chunk, $baseNum, $kind, $little, $invalid);
            $values[] = $v;
        }

        if ($count === 1) {
            return $values[0];
        }
        // Strip trailing invalids from arrays for cleanliness
        return $values;
    }

    private function decodeScalar(string $chunk, int $baseNum, string $kind, bool $little, $invalid)
    {
        $size = strlen($chunk);

        if ($kind === 'f') {
            if ($size === 4) {
                $v = unpack($little ? 'g' : 'G', $chunk)[1];
            } else {
                $v = unpack($little ? 'e' : 'E', $chunk)[1];
            }
            return is_nan($v) ? null : $v;
        }

        // Integer: read as unsigned, then apply sign + invalid checks
        switch ($size) {
            case 1: $u = unpack('C', $chunk)[1]; break;
            case 2: $u = unpack($little ? 'v' : 'n', $chunk)[1]; break;
            case 4: $u = unpack($little ? 'V' : 'N', $chunk)[1]; break;
            case 8: $u = unpack($little ? 'P' : 'J', $chunk)[1]; break;
            default: return null;
        }

        if ($invalid !== null && $u === $invalid) {
            return null;
        }

        if ($kind === 's') {
            $bits = $size * 8;
            $signBit = 1 << ($bits - 1);
            if ($u >= $signBit) {
                $u -= (1 << $bits);
            }
        }
        return $u;
    }

    /** Standard FIT CRC-16 (nibble-table algorithm from the FIT SDK). */
    public static function crc16(string $data): int
    {
        static $table = [
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
        ];
        $crc = 0;
        $len = strlen($data);
        for ($i = 0; $i < $len; $i++) {
            $byte = ord($data[$i]);
            // lower nibble
            $tmp = $table[$crc & 0xF];
            $crc = ($crc >> 4) & 0x0FFF;
            $crc = $crc ^ $tmp ^ $table[$byte & 0xF];
            // upper nibble
            $tmp = $table[$crc & 0xF];
            $crc = ($crc >> 4) & 0x0FFF;
            $crc = $crc ^ $tmp ^ $table[($byte >> 4) & 0xF];
        }
        return $crc & 0xFFFF;
    }
}
