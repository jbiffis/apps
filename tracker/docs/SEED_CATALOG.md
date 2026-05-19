# Seed catalog

The starter set of `event_types`, `event_properties`, and `property_presets` shipped via migrations `V2__seed_presets.sql` and `V3__seed_event_types.sql`.

All seed rows have `is_seed = true` so they cannot be deleted via the API. They can be hidden per-user (Phase 2) but not removed.

## Property presets

| slug | widget | options summary |
|---|---|---|
| `severity-1-5` | `step` | 1 Mild · 2 Annoying · 3 Moderate · 4 Severe · 5 Migraine |
| `face-mood-1-5` | `face_select` | 1 😩 · 2 🙁 · 3 😐 · 4 🙂 · 5 😄 |
| `quality-1-5` | `face_select` | 1 Poor · 2 Fair · 3 OK · 4 Good · 5 Great |
| `side-single` | `single_select` | Left · Right · Both |
| `headache-location-multi` | `multi_select` | Front · Temple · Back · Crown · Behind eyes |
| `body-location-multi` | `multi_select` | Back · Neck · Shoulders · Hips · Knees · Other |
| `with-food-single` | `single_select` | None · Breakfast · Lunch · Dinner · Empty stomach |
| `meal-type-single` | `single_select` | Breakfast · Lunch · Dinner · Snack |
| `bristol-1-7` | `face_select` | 1–7 with shape labels |
| `cough-type-single` | `single_select` | Dry · Wet |
| `flow-1-5` | `step` | 1 None · 2 Spotting · 3 Light · 4 Medium · 5 Heavy |
| `pms-multi` | `multi_select` | Mood · Bloating · Cravings · Breast tender · Headache · Cramps |
| `workout-type-single` | `single_select` | Run · Lift · Yoga · Walk · Bike · Swim · Other |
| `alcohol-type-single` | `single_select` | Beer · Wine · Spirits · Cocktail |
| `cannabis-method-single` | `single_select` | Smoke · Edible · Vape |
| `bool-yes-no` | `bool` | Yes / No |
| `count` | `number` | min 0, default 1 |
| `dose-mg` | `dose` | unit `mg`, step 50 |
| `dose-tablets` | `dose` | unit `tablet(s)`, step 1, default 1 |
| `dose-glasses` | `dose` | unit `glass(es)`, step 1, default 1 |
| `dose-cups` | `dose` | unit `cup(s)`, step 1, default 1 |
| `duration-minutes` | `duration` | unit minutes, default 30 |
| `duration-hours` | `duration` | unit hours, default 7 |
| `temperature-c` | `number` | unit °C, min 34, max 42, step 0.1, default 37 |

## Event type hierarchy

Categories (`is_category = true`) are containers. Leaves are loggable.

### Health (category, icon `Heart`, color `t-coral`)
| name | icon | properties |
|---|---|---|
| Headache | `Habit` (placeholder; pick better) | Severity (`severity-1-5`, required) · Location (`headache-location-multi`) |
| **Eyes** *(category, icon `Sun`)* |  |  |
| ├ Double vision | `Sun` | Severity · Side (`side-single`) |
| ├ Blurry vision | `Sun` | Severity · Side |
| ├ Itchy | `Sun` | Severity · Side |
| └ Bloodshot | `Sun` | Severity · Side |
| **Ears / Nose / Throat** *(category, icon `Mood`)* |  |  |
| ├ Sore throat | `Mood` | Severity |
| ├ Congestion | `Mood` | Severity |
| ├ Earache | `Mood` | Severity · Side |
| └ Cough | `Mood` | Severity · Type (`cough-type-single`) |
| **Digestive** *(category, icon `Food`)* |  |  |
| ├ Stomach pain | `Food` | Severity · Location (`body-location-multi`) |
| ├ Gas | `Food` | Severity |
| ├ Nausea | `Food` | Severity |
| ├ Heartburn | `Food` | Severity |
| └ Bowel movement | `Food` | Bristol scale (`bristol-1-7`) |
| **General body** *(category, icon `Workout`)* |  |  |
| ├ Fatigue | `Workout` | Severity |
| ├ Dizziness | `Workout` | Severity |
| ├ Body pain | `Workout` | Severity · Location (`body-location-multi`) |
| └ Fever | `Workout` | Temperature (`temperature-c`, required) |
| Mood | `Mood` | How (`face-mood-1-5`, required) · Note |

### Medication (category, icon `Pill`, color `t-amber`)
| name | icon | properties |
|---|---|---|
| Advil 200 mg | `Pill` | Dose (`dose-tablets`, default 1) · With (`with-food-single`) |
| Advil 400 mg | `Pill` | Dose · With |
| Tylenol | `Pill` | Dose · With |
| Claritin | `Pill` | Dose · With |
| Reactine | `Pill` | Dose · With |

(`+ Add medication` from the UI creates user-added entries under this category; they're shared.)

### Food (category, icon `Coffee`, color `t-sky`)
| name | icon | properties |
|---|---|---|
| Water | `Water` | Glasses (`dose-glasses`, required, default 1) |
| Coffee | `Coffee` | Cups (`dose-cups`, required, default 1) |
| Tea | `Coffee` | Cups |
| Meal | `Food` | Type (`meal-type-single`, required) · Note |
| Gluten | `Food` | (just an entry — no fields) |
| Sugar treat | `Food` | How much (`severity-1-5`, label 1 "A little" → 5 "A lot") |

### Recreational (category, icon `Sparkle`, color `t-plum`)
| name | icon | properties |
|---|---|---|
| Alcohol | `Coffee` | Drinks (`count`, default 1) · Type (`alcohol-type-single`) |
| Cannabis | `Sparkle` | Method (`cannabis-method-single`, required) · Dose (`count`, default 1) |
| Cigarette | `Sparkle` | Count (`count`, default 1) |

### Activity (category, icon `Steps`, color `t-green`)
| name | icon | properties |
|---|---|---|
| Steps | `Steps` | Count (`count`, required) |
| Workout | `Workout` | Type (`workout-type-single`, required) · Duration (`duration-minutes`) |
| Meditate | `Meditation` | Duration (`duration-minutes`, required) |
| Sleep | `Sleep` | Duration (`duration-hours`, required) · Quality (`quality-1-5`) |

### Lady stuff (category, icon `Heart`, audience `female`)
| name | icon | properties |
|---|---|---|
| Period | `Heart` | Flow (`flow-1-5`, required) |
| Spotting | `Heart` | Severity (`severity-1-5`) |
| Cramps | `Heart` | Severity (`severity-1-5`, required) |
| PMS symptoms | `Heart` | Symptoms (`pms-multi`, required) |

### Journal (no category, icon `Journal`)
Single leaf. Properties: Note (text, `{"placeholder": "What's on your mind?", "maxLength": 5000}`, required).

## Audience defaults

By default on Home:

- **Carley** (female): sees everything in Health, Medication, Food, Recreational, Activity, **Lady stuff**, Journal.
- **Jeremy** (male): sees everything **except Lady stuff**.

Both can opt in/out via the "All trackers → edit" screen (Phase 2).

## Adding to the catalog later

New event types added via the UI go into `event_types` with `is_seed = false`, `created_by = <user>`, and are visible to both users. They can be deleted by the creator. Adding to *this document* and creating a migration is the way to make something a permanent, undeletable seed entry.
