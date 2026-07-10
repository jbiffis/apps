<?php
require __DIR__ . '/_ui.php';

$date = $_GET['date'] ?? '';
$rounds = build_rounds(load_sources());
$round = $rounds[$date] ?? null;

if (!$round) {
    golf_head('Round not found');
    echo '<div class="card"><h2>Round not found</h2><p><a href="index.php">← Back to rounds</a></p></div>';
    golf_foot();
    exit;
}

$sc = $round['scorecard'];
$ac = $round['activity'];
$tz = $round['tz_offset'] ?? 0;
$title = $sc['course']['name'] ?? ($ac['sport'] ?? 'Round');

golf_head($title);
?>
<p style="margin-bottom:10px"><a href="index.php">← All rounds</a></p>

<div class="card">
  <h2 style="font-size:1.25em"><?= h($title) ?></h2>
  <div class="muted"><?= fmt_local($round['start_unix'], $tz, 'l, F j, Y · g:i A') ?></div>

  <?php if ($sc): $t=$sc['totals']; $c=$sc['course']; ?>
    <div class="grid" style="margin-top:14px">
      <div class="stat"><div class="n"><?= $t['score'] ?? '–' ?> <span style="font-size:.5em;font-weight:600"><?= $t['score']!==null?'('.sprintf('%+d',$t['score']-$t['par']).')':'' ?></span></div><div class="l">Score · Par <?= $t['par'] ?></div></div>
      <?php if ($t['front_score']!==null): ?><div class="stat"><div class="n"><?= $t['front_score'] ?> / <?= $t['back_score'] ?></div><div class="l">Front / Back</div></div><?php endif; ?>
      <?php if ($t['putts']!==null): ?><div class="stat"><div class="n"><?= $t['putts'] ?></div><div class="l">Total putts</div></div><?php endif; ?>
      <?php if (!empty($c['tee'])): ?><div class="stat"><div class="n" style="font-size:1.1em"><?= h($c['tee']) ?></div><div class="l">Tees<?= $c['slope']?' · slope '.h($c['slope']):'' ?></div></div><?php endif; ?>
      <?php if (!empty($c['length'])): ?><div class="stat"><div class="n"><?= h($c['length']) ?></div><div class="l">Course length (yd)</div></div><?php endif; ?>
    </div>
  <?php endif; ?>
</div>

<?php if ($sc): ?>
  <div class="card">
    <h2>Scorecard</h2>
    <?php render_scorecard($sc, $tz); ?>
    <?php
      // score breakdown
      $counts=['ace'=>0,'eagle'=>0,'birdie'=>0,'par'=>0,'bogey'=>0,'dbogey'=>0,'worse'=>0];
      foreach($sc['holes'] as $h){ if($h['score']===null||$h['par']===null)continue; $counts[score_class($h['score'],$h['par'])]++; }
      $labels=['eagle'=>'Eagles+','birdie'=>'Birdies','par'=>'Pars','bogey'=>'Bogeys','dbogey'=>'Doubles','worse'=>'Triples+'];
      $counts['eagle']+=$counts['ace'];
    ?>
    <div class="grid" style="margin-top:14px">
      <?php foreach($labels as $k=>$lab): if($counts[$k]<=0 && $k!=='par') continue; ?>
        <div class="stat"><div class="n"><?= $counts[$k] ?></div><div class="l"><?= $lab ?></div></div>
      <?php endforeach; ?>
    </div>
  </div>
<?php endif; ?>

<?php if ($ac): ?>
  <div class="card">
    <h2>Round activity</h2>
    <div class="grid">
      <div class="stat"><div class="n"><?= fmt_hms($ac['elapsed_s']) ?></div><div class="l">Duration</div></div>
      <?php if ($ac['distance_m']!==null): ?><div class="stat"><div class="n"><?= round($ac['distance_m']/1000,2) ?><span style="font-size:.5em"> km</span></div><div class="l">Distance covered</div></div><?php endif; ?>
      <?php if ($ac['calories']!==null): ?><div class="stat"><div class="n"><?= $ac['calories'] ?></div><div class="l">Calories</div></div><?php endif; ?>
      <?php if ($ac['avg_hr']!==null): ?><div class="stat"><div class="n"><?= $ac['avg_hr'] ?> / <?= $ac['max_hr'] ?></div><div class="l">Avg / Max HR</div></div><?php endif; ?>
      <?php if ($ac['ascent_m']!==null): ?><div class="stat"><div class="n"><?= $ac['ascent_m'] ?><span style="font-size:.5em"> m</span></div><div class="l">Ascent</div></div><?php endif; ?>
    </div>
    <?php if (!empty($ac['track'])): ?>
      <h2 style="margin-top:18px">Heart rate</h2>
      <?php render_hr_chart($ac['track']); ?>
    <?php endif; ?>
  </div>
<?php endif; ?>

<?php
  $shots = $sc['shots'] ?? [];
  $holes = $sc['holes'] ?? [];
  $track = $ac['track'] ?? null;
  $hasGeo = (!empty($shots)) || (!empty($track)) || array_filter($holes, fn($h)=>($h['pin_lat']??null)!==null);
?>
<?php if ($hasGeo): ?>
  <div class="card">
    <h2>Round map<?= $shots?' · '.count($shots).' shots':'' ?></h2>
    <?php render_map($track, $shots, $holes); ?>
    <p class="muted" style="font-size:.78em;margin-top:8px">Local geometry from GPS coordinates in the file (no map tiles fetched).</p>
  </div>
<?php endif; ?>

<!-- raw / technical -->
<div class="card">
  <details>
    <summary style="cursor:pointer;font-weight:700;color:var(--green-d)">File details &amp; raw messages</summary>
    <div style="margin-top:12px">
      <?php
        // Show msg counts from each source file
        foreach ($round['source_ids'] as $kind=>$sid):
            $src = load_source($sid); if(!$src) continue;
      ?>
        <p style="margin:8px 0 4px"><b><?= h(ucfirst($kind)) ?> file</b> — <span class="muted"><?= h($src['filename']??'') ?></span></p>
        <div class="scroll"><table style="font-size:.8em">
          <tr><th class="lbl">Global msg</th><th>Count</th></tr>
          <?php foreach ($src['msg_counts'] ?? [] as $g=>$cnt): ?>
            <tr><td class="lbl"><?= msg_name((int)$g) ?></td><td><?= $cnt ?></td></tr>
          <?php endforeach; ?>
        </table></div>
      <?php endforeach; ?>
    </div>
  </details>
</div>

<form method="post" action="delete.php" onsubmit="return confirm('Delete this round?')" style="text-align:right">
  <input type="hidden" name="csrf" value="<?= h(golf_csrf_token()) ?>">
  <input type="hidden" name="date" value="<?= h($date) ?>">
  <button class="btn ghost" type="submit">Delete round</button>
</form>

<?php
golf_foot();

function msg_name(int $g): string {
    static $n=[0=>'file_id',18=>'session',19=>'lap',20=>'record (GPS)',21=>'event',23=>'device_info',
        34=>'activity',49=>'file_creator',190=>'golf: course',191=>'golf: summary',
        192=>'golf: hole result',193=>'golf: hole def',194=>'golf: shot'];
    return h($n[$g] ?? "msg $g");
}
