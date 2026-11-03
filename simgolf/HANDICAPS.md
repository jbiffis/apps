# Season handicap systems

Existing seasons retain `legacy` calculation and ranking behavior. New seasons
created through the API/admin page default to `best_rounds_v1`. The system is
stored on the season and does not change when its name or year is edited.

For the best-rounds system:

| Handicap | Eligible rounds | Calculation |
| --- | --- | --- |
| T1 | First two practice rounds | Combined gross minus combined par |
| T2 | Two practice rounds and first six T1 rounds | Average of the lowest six gross-minus-par values |
| T3 | Two practice rounds and first six rounds of T1 and T2 | Average of the lowest ten gross-minus-par values |

Round order is the stored round number, then ID. Only complete nine-hole scores
count. Missing rounds are excluded, never replaced with zero. Players need at
least 2, 6 or 10 eligible complete rounds, respectively; otherwise their stored
handicap is left unchanged and the admin page reports the shortfall.

The final handicap is rounded to one decimal. Negative handicaps are supported.
There are no per-hole score caps, handicap ceilings or limits on changes between
tournaments. New-system rankings use net scores to one decimal; the original
rounding/tie behavior is retained for legacy seasons.

Use **Calculate T1/T2/T3 Handicaps** on the admin page before entering the target
tournament's scores. Stored handicaps stay fixed during that tournament. Both
calculation and manual handicap changes are rejected once that tournament has
scores. The season's system can only be changed before tournament scores exist;
changing it clears that season's old handicaps so they can be recalculated.

## Deployment

Apply the additive migration before serving the updated API. From the production
Compose directory, with the updated `simgolf/api` checkout available:

```sh
docker compose exec -T simgolf-php php bin/migrate.php
```

For the standalone `simgolf/docker-compose.yml` stack, the service name is `php`.
The migration can be run repeatedly. It adds the system field with `legacy` as
the default for existing data; it never recalculates handicaps or changes scores,
points or prizes. Newly initialized databases include the same field in their
base schema. Deploy the updated API and the committed frontend build together.

In a development database with the migration applied, run the regression checks:

```sh
docker compose exec -T simgolf-php php tests/handicaps.php
```

Database fixtures in this test are rolled back. It checks formula rounding,
round-pool boundaries, varying pars, incomplete scores, high hole scores, and
separate legacy/new ranking behavior.
