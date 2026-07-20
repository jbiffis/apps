package dev.jbiffis.caddie.ble

/**
 * Garmin GDI "FileSyncService" — the newer protobuf-based file enumeration used
 * by devices like the vívoactive 5 (the classic ANT-FS directory is empty).
 *
 * Message tree (from Gadgetbridge's gdi_smart_proto / gdi_file_sync_service):
 *   Smart{ file_sync_service = 43 }
 *   FileSyncService{ file_request=1, file_response=2, file_list_request=9,
 *                    file_list_response=10, new_file_notification=12 }
 *   FileListRequest{ start_page_id=2, flags1=4:FileId, flags2=5:FileId }
 *   FileListResponse{ next_page_id=3, file=4:File(repeated) }
 *   File{ id=1:FileId, type=2:FileType, size=3, page_id=5 }
 *   FileId{ id1=1:fixed64, id2=2:fixed64 }
 *   FileType{ name=2:string, code=3 }
 *   FileRequest{ file=1:File, unk2=2, unk5=5 }
 *   FileResponse{ status=1, handle=3 }
 */
object FileSync {

    const val SMART_FILE_SYNC = 43
    const val FS_FILE_REQUEST = 1
    const val FS_FILE_RESPONSE = 2
    const val FS_FILE_LIST_REQUEST = 9
    const val FS_FILE_LIST_RESPONSE = 10
    const val FS_NEW_FILE_NOTIFICATION = 12

    private const val FLAG_MAGIC = 42405L // 0xa5a5, from Gadgetbridge

    data class FileId(val id1: Long, val id2: Long)
    data class RemoteFile(
        val id: FileId?,
        val typeName: String?,
        val typeCode: Int?,
        val size: Long,
        val pageId: Int?,
        /** The exact File sub-message bytes as received — echoed back in a FileRequest. */
        val raw: ByteArray = ByteArray(0),
    )
    data class FileList(val nextPageId: Int?, val files: List<RemoteFile>)

    // ---- Build outgoing Smart messages ----------------------------------------

    private fun fileIdWriter(f: FileId) = Protobuf.Writer().fixed64(1, f.id1).fixed64(2, f.id2)

    /** Smart{ FileSyncService{ FileListRequest{ flags1, flags2, [start_page_id] } } }. */
    fun buildFileListRequest(startPageId: Int?): ByteArray {
        val flags = FileId(FLAG_MAGIC, FLAG_MAGIC)
        val req = Protobuf.Writer()
        if (startPageId != null) req.uint32(2, startPageId)
        req.message(4, fileIdWriter(flags))
        req.message(5, fileIdWriter(flags))
        val fss = Protobuf.Writer().message(FS_FILE_LIST_REQUEST, req)
        return Protobuf.Writer().message(SMART_FILE_SYNC, fss).toByteArray()
    }

    /**
     * Smart{ FileSyncService{ FileRequest{ file, unk2=24, unk3=0, unk4=0, unk5=15 } } }.
     * The File must be echoed back exactly as received (Gadgetbridge sets
     * file = fileToRequest), so we embed the raw File bytes verbatim.
     */
    fun buildFileRequest(file: RemoteFile): ByteArray {
        val fileBytes = if (file.raw.isNotEmpty()) file.raw else run {
            // Fallback reconstruction (used only in tests)
            val m = Protobuf.Writer()
            file.id?.let { m.message(1, fileIdWriter(it)) }
            val t = Protobuf.Writer()
            file.typeName?.let { t.bytes(2, it.toByteArray(Charsets.UTF_8)) }
            file.typeCode?.let { t.uint32(3, it) }
            m.message(2, t)
            m.toByteArray()
        }
        val req = Protobuf.Writer()
            .bytes(1, fileBytes)
            .uint32(2, 24).uint32(3, 0).uint32(4, 0).uint32(5, 15)
        val fss = Protobuf.Writer().message(FS_FILE_REQUEST, req)
        return Protobuf.Writer().message(SMART_FILE_SYNC, fss).toByteArray()
    }

    // ---- Parse incoming Smart messages ----------------------------------------

    /** Extract the FileSyncService sub-message bytes from a Smart message, if present. */
    fun fileSyncServiceOf(smart: ByteArray): ByteArray? =
        Protobuf.firstBytes(Protobuf.decode(smart), SMART_FILE_SYNC)

    private fun parseFileId(bytes: ByteArray): FileId {
        val f = Protobuf.decode(bytes)
        return FileId(Protobuf.firstVarint(f, 1) ?: 0, Protobuf.firstVarint(f, 2) ?: 0)
    }

    private fun parseFile(bytes: ByteArray): RemoteFile {
        val f = Protobuf.decode(bytes)
        val typeBytes = Protobuf.firstBytes(f, 2)
        var name: String? = null
        var code: Int? = null
        if (typeBytes != null) {
            val t = Protobuf.decode(typeBytes)
            name = Protobuf.firstBytes(t, 2)?.toString(Charsets.UTF_8)
            code = Protobuf.firstVarint(t, 3)?.toInt()
        }
        return RemoteFile(
            id = Protobuf.firstBytes(f, 1)?.let { parseFileId(it) },
            typeName = name,
            typeCode = code,
            size = Protobuf.firstVarint(f, 3) ?: 0,
            pageId = Protobuf.firstVarint(f, 5)?.toInt(),
            raw = bytes,
        )
    }

    /** Parse a FileSyncService message; returns a FileList if it holds a FileListResponse. */
    fun parseFileListResponse(fileSyncService: ByteArray): FileList? {
        val fss = Protobuf.decode(fileSyncService)
        val resp = Protobuf.firstBytes(fss, FS_FILE_LIST_RESPONSE) ?: return null
        val r = Protobuf.decode(resp)
        return FileList(
            nextPageId = Protobuf.firstVarint(r, 3)?.toInt(),
            files = Protobuf.allBytes(r, 4).map { parseFile(it) },
        )
    }

    /** Parse a NewFileNotification (pushed by the watch) into its file list. */
    fun parseNewFileNotification(fileSyncService: ByteArray): List<RemoteFile>? {
        val fss = Protobuf.decode(fileSyncService)
        val notif = Protobuf.firstBytes(fss, FS_NEW_FILE_NOTIFICATION) ?: return null
        return Protobuf.allBytes(Protobuf.decode(notif), 1).map { parseFile(it) }
    }

    /** Parse a FileResponse; returns the transfer handle if the request succeeded. */
    fun parseFileResponseHandle(fileSyncService: ByteArray): Int? {
        val fss = Protobuf.decode(fileSyncService)
        val resp = Protobuf.firstBytes(fss, FS_FILE_RESPONSE) ?: return null
        val r = Protobuf.decode(resp)
        val status = Protobuf.firstVarint(r, 1) ?: 0
        if (status != 0L) return null
        return Protobuf.firstVarint(r, 3)?.toInt()
    }
}
