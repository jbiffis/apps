<?php
require __DIR__ . '/_ui.php';

$sources = load_sources();
$rounds  = build_rounds($sources);
$stats   = aggregate_stats($rounds);
$flash   = $_SESSION['golf_flash'] ?? null;
unset($_SESSION['golf_flash']);

golf_head('Rounds', 'home');

if ($flash): ?>
  <div class="flash <?= $flash['type']==='ok'?'ok':'err' ?>"><?= $flash['msg'] /* pre-escaped */ ?></div>
<?php endif;

if (!$rounds): ?>
  <div class="card" style="text-align:center">
    <h2>No rounds yet</h2>
    <p class="muted">Upload a Garmin <code>.FIT</code> file from your watch to get started.</p>
  </div>
<?php else: ?>

  <!-- ===== aggregate stats ===== -->
  <div class="card">
    <h2>Your golf at a glance</h2>
    <div class="grid">
      <div class="stat"><div class="n"><?= $stats['rounds'] ?></div><div class="l">Rounds logged</div></div>
      <?php if (!empty($stats['scored_rounds'])): ?>
        <div class="stat"><div class="n"><?= round($stats['avg_score'],1) ?></div><div class="l">Avg score</div></div>
        <div class="stat"><div class="n"><?= sprintf('%+.1f',$stats['avg_to_par']) ?></div><div class="l">Avg to par</div></div>
        <div class="stat"><div class="n"><?= $stats['best_score'] ?> <span style="font-size:.5em;font-weight:600">(<?= sprintf('%+d',$stats['best_diff']) ?>)</span></div><div class="l">Best round</div></div>
        <?php if ($stats['avg_putts']!==null): ?>
          <div class="stat"><div class="n"><?= round($stats['avg_putts'],1) ?></div><div class="l">Avg putts</div></div>
        <?php endif; ?>
      <?php endif; ?>
      <?php if (!empty($stats['activity_rounds'])): ?>
        <div class="stat"><div class="n"><?= round($stats['total_distance_m']/1000,1) ?><span style="font-size:.5em"> km</span></div><div class="l">Distance walked</div></div>
      <?php endif; ?>
    </div>

    <?php if (!empty($stats['history']) && count($stats['history'])>1): ?>
      <h2 style="margin-top:18px">Scoring trend</h2>
      <?php render_trend($stats['history']); ?>
    <?php endif; ?>
  </div>

  <!-- ===== round list ===== -->
  <?php foreach ($rounds as $r):
      $sc = $r['scorecard']; $ac = $r['activity']; $tz = $r['tz_offset'] ?? 0;
      $name = $sc['course']['name'] ?? ($ac['sport'] ?? 'Round');
      $t = $sc['totals'] ?? null;
  ?>
    <a href="round.php?date=<?= urlencode($r['date']) ?>" class="card roundcard" style="color:inherit">
      <?php if ($t && $t['score']!==null): ?>
        <div>
          <div class="big"><?= $t['score'] ?></div>
          <div class="muted" style="font-size:.8em;text-align:center"><?= sprintf('%+d',$t['score']-$t['par']) ?></div>
        </div>
      <?php else: ?>
        <div class="big" style="font-size:1.4em">⛳</div>
      <?php endif; ?>
      <div style="flex:1;min-width:160px">
        <div style="font-weight:700"><?= h($name) ?></div>
        <div class="muted" style="font-size:.85em"><?= fmt_local($r['start_unix'], $tz, 'l, M j, Y') ?></div>
        <div style="margin-top:5px">
          <?php if ($sc): ?><span class="pill">Scorecard</span> <?php endif; ?>
          <?php if ($ac): ?><span class="pill"><?= fmt_hms($ac['elapsed_s']) ?> · <?= round($ac['distance_m']/1000,1) ?>km</span> <?php endif; ?>
          <?php if ($t && $t['putts']!==null): ?><span class="pill"><?= $t['putts'] ?> putts</span><?php endif; ?>
        </div>
      </div>
      <div class="muted">›</div>
    </a>
  <?php endforeach; ?>
<?php endif; ?>

  <!-- ===== upload ===== -->
  <div class="card" id="upload">
    <h2>Import a round</h2>
    <form method="post" action="upload.php" enctype="multipart/form-data">
      <input type="hidden" name="csrf" value="<?= h(golf_csrf_token()) ?>">
      <div class="dropzone">
        <div style="font-size:1.6em">⛳</div>
        <p class="muted">Select Garmin <code>.FIT</code> files — <b>scorecard</b> (<code>SCORE_*</code> / <code>Golf-SCORECARD_RAWDATA-*</code>) and/or <b>activity</b> (<code>ACTIVITY_*</code>). Files from the same day merge into one round.</p>
        <input type="file" name="fit[]" accept=".fit,.FIT" multiple required>
        <br><button class="btn" type="submit">Upload &amp; parse</button>
      </div>
    </form>
    <p class="muted" style="font-size:.8em;margin-top:10px">
      On your watch (USB): look in <code>GARMIN/</code> for <code>Activity/</code> and scorecard files.
      Everything is parsed on the server; nothing is sent anywhere else.
    </p>
  </div>

<?php
golf_foot();

/** Small inline SVG trend of score-to-par across rounds (oldest→newest). */
function render_trend(array $history, int $w=820, int $hgt=160): void {
    $diffs = array_column($history,'diff');
    $n=count($diffs); $pad=26;
    $mn=min($diffs);$mx=max($diffs); $lo=min(0,$mn)-1;$hi=max(0,$mx)+1; $range=max($hi-$lo,1);
    $x=fn($i)=>$pad+($n<2?0:($i/($n-1))*($w-2*$pad));
    $y=fn($v)=>$pad+(1-($v-$lo)/$range)*($hgt-2*$pad);
    echo '<div class="scroll"><svg viewBox="0 0 '.$w.' '.$hgt.'" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Scoring trend">';
    $zy=round($y(0),1);
    echo '<line x1="'.$pad.'" y1="'.$zy.'" x2="'.($w-$pad).'" y2="'.$zy.'" stroke="#9ec2a9" stroke-dasharray="3 3"/>';
    echo '<text x="2" y="'.($zy-3).'" font-size="9" fill="#6b7d72">par</text>';
    $line='';
    foreach($diffs as $i=>$d){ $xi=round($x($i),1);$yi=round($y($d),1); $line.=($i?'L':'M')."$xi $yi "; }
    echo '<path d="'.$line.'" fill="none" stroke="#2e7d46" stroke-width="2"/>';
    foreach($diffs as $i=>$d){ echo '<circle cx="'.round($x($i),1).'" cy="'.round($y($d),1).'" r="3" fill="#2e7d46"/>'; }
    echo '</svg></div>';
}
