<?php
/** Shared UI: page chrome, score badges, SVG charts/maps. */
require_once __DIR__ . '/golf.php';

function golf_head(string $title, string $active = ''): void { ?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="color-scheme" content="light dark">
<title><?= h($title) ?> · Golf Stats</title>
<style>
  :root{
    --bg:#f4f6f4; --card:#fff; --ink:#1c2b22; --muted:#6b7d72; --line:#e2e8e3;
    --green:#2e7d46; --green-d:#1f5d33; --fair:#eaf5ec; --accent:#c0392b;
    --shadow:0 1px 3px rgba(0,0,0,.08);
  }
  @media (prefers-color-scheme: dark){
    :root{ --bg:#121712; --card:#1b241d; --ink:#e6efe8; --muted:#93a698;
      --line:#2b3830; --fair:#20301f; --shadow:0 1px 3px rgba(0,0,0,.4); }
  }
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
    background:var(--bg);color:var(--ink);line-height:1.45;-webkit-text-size-adjust:100%}
  a{color:var(--green);text-decoration:none}
  a:hover{text-decoration:underline}
  .wrap{max-width:1000px;margin:0 auto;padding:16px}
  header.top{background:var(--green-d);color:#fff;padding:14px 16px;position:sticky;top:0;z-index:5;
    box-shadow:var(--shadow)}
  header.top .wrap{padding:0;display:flex;align-items:center;gap:14px}
  header.top h1{font-size:1.15em;font-weight:700;margin-right:auto}
  header.top a{color:#dff3e4;font-size:.9em;font-weight:600}
  header.top a.on{color:#fff;text-decoration:underline}
  .card{background:var(--card);border:1px solid var(--line);border-radius:12px;
    box-shadow:var(--shadow);padding:16px;margin-bottom:16px}
  h2{font-size:1.05em;margin-bottom:12px;color:var(--green-d)}
  @media (prefers-color-scheme: dark){h2{color:#7fce97}}
  .muted{color:var(--muted)}
  .grid{display:grid;gap:12px;grid-template-columns:repeat(auto-fit,minmax(130px,1fr))}
  .stat{background:var(--fair);border-radius:10px;padding:12px 14px}
  .stat .n{font-size:1.5em;font-weight:800;color:var(--green-d)}
  @media (prefers-color-scheme: dark){.stat .n{color:#8fd9a5}}
  .stat .l{font-size:.72em;text-transform:uppercase;letter-spacing:.04em;color:var(--muted)}
  table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}
  th,td{padding:7px 6px;text-align:center;border-bottom:1px solid var(--line);font-size:.86em;white-space:nowrap}
  th{color:var(--muted);font-weight:600;font-size:.72em;text-transform:uppercase}
  td.lbl,th.lbl{text-align:left;color:var(--muted);font-weight:600}
  tr.sub td{background:var(--fair);font-weight:700}
  .badge{display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:26px;
    padding:0 5px;border-radius:6px;font-weight:800;font-size:.9em}
  .b-ace,.b-eagle{background:#f6c945;color:#3a2c00}
  .b-birdie{background:#e05a4d;color:#fff;border-radius:50%}
  .b-par{background:transparent;color:var(--ink)}
  .b-bogey{background:#5b8fd6;color:#fff}
  .b-dbogey{background:#3a5a8a;color:#fff}
  .b-worse{background:#2a2f45;color:#fff}
  .b-none{color:var(--muted)}
  .btn{display:inline-block;background:var(--green);color:#fff;border:none;border-radius:8px;
    padding:11px 20px;font-size:1em;font-weight:700;cursor:pointer}
  .btn:hover{background:var(--green-d);text-decoration:none}
  .btn.sm{padding:6px 12px;font-size:.85em}
  .btn.ghost{background:transparent;color:var(--accent);border:1px solid var(--line)}
  .roundcard{display:flex;align-items:center;gap:14px;flex-wrap:wrap}
  .roundcard .big{font-size:1.8em;font-weight:800;line-height:1}
  .pill{display:inline-block;font-size:.7em;font-weight:700;padding:2px 8px;border-radius:999px;
    background:var(--fair);color:var(--green-d);text-transform:uppercase;letter-spacing:.03em}
  @media (prefers-color-scheme: dark){.pill{color:#8fd9a5}}
  .flash{padding:12px 14px;border-radius:10px;margin-bottom:16px;font-weight:600}
  .flash.ok{background:#e7f6ec;color:#1f5d33}
  .flash.err{background:#fdecea;color:#a5281b}
  .dropzone{border:2px dashed var(--line);border-radius:12px;padding:22px;text-align:center;background:var(--fair)}
  .dropzone input[type=file]{margin:10px 0}
  svg{max-width:100%;height:auto;display:block}
  .scroll{overflow-x:auto}
  .legend{display:flex;gap:10px;flex-wrap:wrap;font-size:.75em;color:var(--muted);margin-top:8px}
  .legend span{display:inline-flex;align-items:center;gap:4px}
  .dot{width:10px;height:10px;border-radius:50%;display:inline-block}
</style>
</head>
<body>
<header class="top"><div class="wrap">
  <h1>⛳ Golf Stats</h1>
  <a href="index.php" class="<?= $active==='home'?'on':'' ?>">Rounds</a>
  <a href="index.php#upload" class="<?= $active==='upload'?'on':'' ?>">Upload</a>
</div></header>
<div class="wrap">
<?php }

function golf_foot(): void { ?>
<footer class="muted" style="text-align:center;font-size:.78em;padding:20px 0 40px">
  Parses Garmin <code>.FIT</code> files locally — nothing leaves your server.
</footer>
</div></body></html>
<?php }

function badge(?int $score, ?int $par): string {
    $cls = score_class($score, $par);
    $txt = $score === null ? '–' : (string)$score;
    return '<span class="badge b-' . $cls . '" title="' . h(score_label($score,$par)) . '">' . $txt . '</span>';
}

function fmt_local(?int $unix, int $tz, string $fmt='M j, Y · g:i A'): string {
    return $unix === null ? '—' : gmdate($fmt, $unix + $tz);
}

/** Render the 9-out / 9-in scorecard table. */
function render_scorecard(array $sc, int $tz): void {
    $holes = $sc['holes'];
    $byNum = [];
    foreach ($holes as $h) $byNum[$h['number']] = $h;
    $t = $sc['totals'];

    $nines = [[1,9,'OUT'],[10,18,'IN']];
    echo '<div class="scroll"><table>';
    foreach ($nines as [$lo,$hi,$lbl]) {
        // header
        echo '<tr><th class="lbl">Hole</th>';
        for($n=$lo;$n<=$hi;$n++) echo '<th>'.$n.'</th>';
        echo '<th>'.$lbl.'</th></tr>';
        // par
        echo '<tr><td class="lbl">Par</td>'; $ps=0;
        for($n=$lo;$n<=$hi;$n++){ $p=$byNum[$n]['par']??null; $ps+=$p??0; echo '<td>'.($p??'–').'</td>'; }
        echo '<td><b>'.$ps.'</b></td></tr>';
        // yards
        echo '<tr class="muted"><td class="lbl">Yds</td>';
        for($n=$lo;$n<=$hi;$n++){ $y=$byNum[$n]['length_yd']??null; echo '<td style="font-size:.8em">'.($y??'–').'</td>'; }
        $yt=0; for($n=$lo;$n<=$hi;$n++) $yt+=$byNum[$n]['length_yd']??0;
        echo '<td style="font-size:.8em"><b>'.$yt.'</b></td></tr>';
        // score
        echo '<tr class="sub"><td class="lbl">Score</td>'; $ss=0; $any=false;
        for($n=$lo;$n<=$hi;$n++){ $h=$byNum[$n]??[]; $s=$h['score']??null; if($s!==null){$ss+=$s;$any=true;} echo '<td>'.badge($s,$h['par']??null).'</td>'; }
        echo '<td><b>'.($any?$ss:'–').'</b></td></tr>';
        // putts
        echo '<tr class="muted"><td class="lbl">Putts</td>'; $pt=0;
        for($n=$lo;$n<=$hi;$n++){ $p=$byNum[$n]['putts']??null; $pt+=$p??0; echo '<td style="font-size:.8em">'.($p??'–').'</td>'; }
        echo '<td style="font-size:.8em"><b>'.$pt.'</b></td></tr>';
    }
    echo '</table></div>';
}

/**
 * Render an inline SVG of the round: GPS walk track (if any), shot lines,
 * and pin positions. Pure geometry, no external tiles.
 */
function render_map(?array $track, array $shots, array $holes, int $w=820, int $hgt=560): void {
    // collect points for bounds
    $pts = [];
    if ($track) foreach ($track as $p) if ($p[0]!==null) $pts[] = [$p[0],$p[1]];
    foreach ($shots as $s){ if($s['slat']!==null)$pts[]=[$s['slat'],$s['slon']]; if($s['elat']!==null)$pts[]=[$s['elat'],$s['elon']]; }
    foreach ($holes as $h){ if(($h['pin_lat']??null)!==null)$pts[]=[$h['pin_lat'],$h['pin_lon']]; }
    if (count($pts) < 2){ echo '<p class="muted">No GPS coordinates in this round.</p>'; return; }

    // Robust bounds: trim a small percentile off each end so a few stray GPS
    // fixes don't blow up the scale. Points outside the box are clipped by the
    // SVG viewport. Fall back to min/max for small point sets.
    $lats=array_column($pts,0); $lons=array_column($pts,1);
    sort($lats); sort($lons);
    $pct=function(array $a,float $p){ $i=(int)round($p*(count($a)-1)); return $a[max(0,min(count($a)-1,$i))]; };
    if (count($pts) >= 40) {
        $minLat=$pct($lats,0.02);$maxLat=$pct($lats,0.98);
        $minLon=$pct($lons,0.02);$maxLon=$pct($lons,0.98);
    } else {
        $minLat=min($lats);$maxLat=max($lats);$minLon=min($lons);$maxLon=max($lons);
    }
    $midLat=($minLat+$maxLat)/2; $kx=cos(deg2rad($midLat));
    $pad=28;
    $spanX=max(($maxLon-$minLon)*$kx, 1e-6); $spanY=max($maxLat-$minLat,1e-6);
    $scale=min(($w-2*$pad)/$spanX, ($hgt-2*$pad)/$spanY);
    $ox=($w-$spanX*$scale)/2; $oy=($hgt-$spanY*$scale)/2;
    $px=function($lat,$lon) use($minLon,$maxLat,$kx,$scale,$ox,$oy){
        return [ $ox+($lon-$minLon)*$kx*$scale, $oy+($maxLat-$lat)*$scale ];
    };

    echo '<svg viewBox="0 0 '.$w.' '.$hgt.'" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Round map">';
    echo '<rect width="'.$w.'" height="'.$hgt.'" rx="12" fill="#dcece0"/>';
    // walk track
    if ($track){
        $d=''; $started=false;
        foreach($track as $p){ if($p[0]===null)continue; [$x,$y]=$px($p[0],$p[1]); $d.=($started?'L':'M').round($x,1).' '.round($y,1).' '; $started=true; }
        if($d) echo '<path d="'.$d.'" fill="none" stroke="#3f8f5c" stroke-width="2" stroke-opacity=".55" stroke-linejoin="round"/>';
    }
    // shots
    foreach($shots as $s){
        if($s['slat']===null||$s['elat']===null)continue;
        [$x1,$y1]=$px($s['slat'],$s['slon']); [$x2,$y2]=$px($s['elat'],$s['elon']);
        echo '<line x1="'.round($x1,1).'" y1="'.round($y1,1).'" x2="'.round($x2,1).'" y2="'.round($y2,1).'" stroke="#c0392b" stroke-width="1.6" stroke-opacity=".8"/>';
        echo '<circle cx="'.round($x1,1).'" cy="'.round($y1,1).'" r="2.4" fill="#c0392b"/>';
    }
    // pins
    foreach($holes as $h){
        if(($h['pin_lat']??null)===null)continue;
        [$x,$y]=$px($h['pin_lat'],$h['pin_lon']);
        echo '<circle cx="'.round($x,1).'" cy="'.round($y,1).'" r="3.2" fill="#1f5d33" stroke="#fff" stroke-width="1"/>';
        echo '<text x="'.round($x+5,1).'" y="'.round($y+3,1).'" font-size="9" fill="#14401f">'.($h['number']??'').'</text>';
    }
    echo '</svg>';
    echo '<div class="legend">'
        .'<span><i class="dot" style="background:#3f8f5c"></i>Walk track</span>'
        .'<span><i class="dot" style="background:#c0392b"></i>Shots</span>'
        .'<span><i class="dot" style="background:#1f5d33"></i>Pins</span></div>';
}

/** Inline SVG heart-rate area chart from the activity track. */
function render_hr_chart(array $track, int $w=820, int $hgt=180): void {
    $series=[]; $t0=null;
    foreach($track as $p){ if(($p[2]??null)!==null){ $t=$p[4]??null; if($t0===null&&$t!==null)$t0=$t; $series[]=[$t,$p[2]]; } }
    if (count($series)<2){ echo '<p class="muted">No heart-rate data.</p>'; return; }
    $hrs=array_column($series,1); $mn=min($hrs);$mx=max($hrs);
    $lo=max(40,$mn-5); $hi=$mx+5; $range=max($hi-$lo,1);
    $n=count($series); $pad=24;
    $x=fn($i)=>$pad+($i/($n-1))*($w-2*$pad);
    $y=fn($v)=>$pad+(1-($v-$lo)/$range)*($hgt-2*$pad);
    $line=''; $area='';
    foreach($series as $i=>$s){ $xi=round($x($i),1); $yi=round($y($s[1]),1); $line.=($i?'L':'M')."$xi $yi "; }
    $area="M".round($x(0),1)." ".round($hgt-$pad,1)." ".ltrim($line,'M');
    $area.="L".round($x($n-1),1)." ".round($hgt-$pad,1)." Z";
    echo '<svg viewBox="0 0 '.$w.' '.$hgt.'" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Heart rate">';
    foreach([$lo,($lo+$hi)/2,$hi] as $g){ $gy=round($y($g),1); echo '<line x1="'.$pad.'" y1="'.$gy.'" x2="'.($w-$pad).'" y2="'.$gy.'" stroke="#cfe0d4" stroke-width="1"/>'; echo '<text x="2" y="'.($gy+3).'" font-size="9" fill="#6b7d72">'.round($g).'</text>'; }
    echo '<path d="'.$area.'" fill="#e05a4d" fill-opacity=".14"/>';
    echo '<path d="'.$line.'" fill="none" stroke="#e05a4d" stroke-width="1.6"/>';
    echo '</svg>';
}
