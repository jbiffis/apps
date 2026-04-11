
I am building a web app to track scores of a simulation golf league. There are 8 players who get together every week and play a 9-hole round of golf. I need to keep score, tracking handicap and more throughout the tournament. 

## Overall Format
- 20 weeks
- 2 weeks of practice rounds - These will count towards establishing the handicap, but are not scored in the overall tournament
- 3 6-week tournaments. Each tournament will be 3 18-hole courses with 9 holes played each week. 
- Scores will be kept for each 6-week tournament
- Each 6-week tournament will have a winner
- Scoring is also for the overall winners. This include all the 3 6-week tournaments, but not the first rounds of practice. 
- Handicaps are established in the first two rounds (practice rounds)
	- For example, if par for the two rounds was a total of 72 and a player scored 82, they have a handicap for the first tournament of 10
	- After each tournament, the handicap for the next tournament is adjusted. 

## How Scoring Works
- Each week points will be assigned based on the handicap-adjusted score of the round. 
- The player with the lowest score under par will receive 8 points
- The next lowest score will receive 7 points
- The worst (highest) score will get 1 point
- If there are players tied, the points for those position are evenly distributed amongst those players
	- For example, if 3 players have the lowest handicap adjusted score, they would each get (8 + 7 + 6) / 3 = 7 points each. 
- If a player misses a round, they automatically have the highest (worst) score, and get 1 point. 
	- Again, if multiple people miss the round, they share the points. So if 2 people miss the round, they get (1 + 2) / 2 = 1.5 points each.

### Prize Money
- Everyone pays $200 to get in, plus $2 each round for the chip in contest
- Tournament prizes are $200 for first place, $50 for second place
- Each week $20 is for the closest to the pin. Players must land on the green to be eligible. If no winner, the $20 goes to the next week.
- Every week everyone puts $2 into the chip in contest pot (not everyone is present every week).
	- When someone chips in the ball to the hole, they get the prize pot. This can happen anytime. If there is no money in the prize pot, they get $0. 
- 


## Web App requirements

### General Notes
- This web app will primarily be used on mobile. Ensure everything looks good and fits correctly on mobile. 
- Color scheme should be similar to the Masters website. 
- Please include simple and tasteful graphics throughout the app
- Use the following google font https://fonts.google.com/specimen/Playfair+Display?categoryFilters=Serif:%2FSerif%2F*,%2FSlab%2F
- The web app should support PWA formats to make it feel as native as possible on both Android and iOS.

### Season Summary page

This page should display the overall scores for the tournament example of the data that should be displayed:

| Pos | Name | T 1 | T 2 | T 3 | Overall |
| --- | ---- | --- | --- | --- | ------- |
| 1   | Shea | 32  | 23  | 16  | 71      |
- The names should link to the Player page
- The "T N" headings should link to the Tournament page
- This page it should have a dropdown at the top to select the season you're interested in 

### Player Page
- This page it should have a dropdown at the top to select the season you're interested in 
- It will then show the players scores for each round (including the handicap rounds)

Format;
##### Tournament 1

| Round                   | Points |
| ----------------------- | ------ |
| 1 - Course Name - Front | 3      |
| 2 - Course Name - Back  | 8      |
| 3 - Course Name - Front | 1      |
| 4 - Course Name - Back  | 3      |
| 5 - Course Name - Back  | 8      |
| 6 - Course Name - Front | 1      |
| Tournament 1 Total      | 24     |
Etc for each tournament that season

- Rounds should be clickable and link to the round page. 
- After the last tournament it should show the overall points for all 3 tournaments

Below the handicap and 3 tournament sections, there should be a section for Prize Winnings
It will list all prize winnings for a player

Example: 

**Prize Winnings**
Round 1 - Closest to the Pin - $20
Tournament 1 - First place - $200
Tournament 3 - Second Place - $50
Round 6 - Chip in winner - $32

Total Pize Winnings - $302

Prize winnings should also link to the Prize Winnings page

### Tournament Page

| Pos | Name | Round 1 |        | Round 2 |        | Round 3 |        | etc | Total Points |
| --- | ---- | ------- | ------ | ------- | ------ | ------- | ------ | --- | ------------ |
|     |      | Score   | Points | Score   | Points | Score   | Points | etc |              |
| 1   | Shea | 32      | 3      | 23      | 8      | 16      | 5      | etc | 65           |
- Scores should be handicap-adjusted scores
- This page should show all players in the round
- Should show all rounds
- On mobile, the table should show the position and name column at all times, but as the user scrolls sideways, is slides through the rounds. Only a couple of rounds will be visible in the screen viewport. Do not try to fit the whole table in the viewport 
- Names should link to the player page
- Round headings should link to the round page.

### Round Page
This should show the scorecard for the round

Example format

|         | Hole 1 | etc | Gross Score | Handicap | Net Score | Points |
| ------- | ------ | --- | ----------- | -------- | --------- | ------ |
| Yardage | 354    |     | 3254        |          |           |        |
| Par     | 4      |     | 36          |          |           |        |
|         |        |     |             |          |           |        |
| Shea    | 6      |     | 38          | -4       | 34        | 4      |

- The points are based on the scoring detailed above. 
- Player name should be linked to the player page
- This table should scroll similar as the tournament page one. The first column should always be visible. as well as the first 2-3 columns. As the user swipes sideways it should scroll through the rest of the columns to the right. 
- Ensure the player scores are shown in proper golf format 
	- Circle around the score for birdie (1 below par)
	- Two circles around the score for Eagle (2 below par)
	- No marking for Par
	- Square around the score for bogie (1 over par)
	- Double square around the score for double bogie (2 over par)
	- Solid square for anything over 2 over par. 
- Handicap header should link to handicap page

Below this table there should be a "Closest to the pin" section

This should have a list of players and the distance they were closest to the pin. Not all players will be in this list.
The winner (closest to pin) should be highlighted in some fashion.
Example:

**Closest to the Pin**
Hole 3 - 154 yards

| Name  | Distance |
| ----- | -------- |
| Shea  | 12.6     |
| Sonat | 16.3     |
The Round page should have an edit button at the top. Once clicked
	- It should confirm you want to be in edit mode
	- allow you to make change to any of the raw scores (not the totals or handicap)
	- The Edit button should change to a "Save" button
	- Upon clicking Save - confirm you want to make the changes or cancel
	- Upon clicking confirm, update the database for that round.
	- Keep track of the edit in an edits table 
	- Display the edits on the round page
	- Example "2026-04-10 Shea Hole 3 edited from 3 to 5"
### Handicap Page

Three tables here, showing the handicap for each tournament 

Tournament 1 

| Name  | Hanidcap |
| ----- | -------- |
| Shea  | 13       |
| Sonat | 5        |
| etc   | etc      |

### Prize winnings page

There should be a season switcher at the top of this page.
A list of names and the total prize winnings for the season

| Name  | Prize Purse |
| ----- | ----------- |
| Shea  | $302        |
| Sonat | $123        |
| etc   | etc         |
Clicking on each name brings the user to the player page, scrolled down to the prize winnings section. 


### In-Round page
While a round is being played, we need to enter some information
- Which hole is closest to the pin
- What the yardage of closes to the pin is
- Add a player who gets closest to the pin
	- What their distance is
- A button to say "no more golfers" 
	- When that button is pressed, closest to the pin is awarded
	- If no golfers have made it to the green (no players added in step above), then display a message of "No golfers made the green, moving the $20 for next week" with a "Confirm" or "Cancel" button
	- If confirmed, adjust the prize allocation for the next round to be $20 more.
- A button for "Golfer Chipped In"
	- A dialog should appear with 
		- A drop down of the golfers names
		- a text box entry with a label "Prize Winnings" 
	- Two buttons "Confirm" or "Cancel"
	- Upon confirm, add the player name and winnings to the winnings table
- Update the UI to reflect the winners
