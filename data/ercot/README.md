# ERCOT scenario data #
This folder contains scenario data from the Electric Reliability Council of Texas electricity (ERCOT). The data is based on data from 2015-2016. This data is reformatted to make 52 'hypothetical' scenario's of five days long, that all start at 01/04/2016 and end at 05/04/2016.

## Description of the files ##
* `price_up/down.csv`: the imbalance prices up and down. 
* `cprice_up/down.csv`: the capacity reserve prices up and down. 
* `da.csv`: the day ahead market prices (one scenario, from 2016).
* `ptu_up/down.csv`: the reserve usage up and down.
* `AP_up/down.csv` and `ER_up/down.csv`. These files are used by the Linear Approximation method. They contain the coefficients for the piece-wise linear functions that describe the Expected Return (ER) and the Acceptance Probability (AP). These values are obtained by a script. The script is available on request.

## References ##
1. ERCOT, Market information, http://www.ercot.com/mktinfo/, 2015-2016, Accessed: 2018-02-27.
2. ERCOT, Fast responding regulation service pilot, http://www.ercot.com/mktrules/pilots/frrs, 2016, Accessed: 2018-03-14.

## Scenario's ##
The scenario's in the files are not in the logical order. The scenario order is 01-01-2016 - 11-06-2016, 01-07-2015 - 11-12-2015 [Data is only available for those months, and the first eleven days of those months].
Every scenario consists of two days - weekends are also taken into account. After the two days, the two days are repeated to make one week

### Scenario order ###
S1  4/4  - 8/4
S2  11/4 - 15/4
S3  18/4 - 22/4
S4  25/4 - 29/4 
S5  2/5  - 6/5
S6  9/5  - 13/5
S7  16/5 - 20/5
S8  23/5 - 27/5
S9  30/5 - 3/6
S10 6/6  - 10/6
S11 13/6 - 17/6
S12 20/6 - 24/6
S13 27/6 - 01/7
S14 4/7  - 8/7
S15 11/7 - 15/7
S16 18/7 - 22/7
S17 25/7 - 29/7
S18 1/8  - 5/8
S19 8/8  - 12/8
S20 15/8 - 19/8
S21 22/8 - 26/8
S22 29/8 - 2/9
S23 5/9  - 9/9
S24 12/9 - 16/9
S25 19/9 - 23/9
S26 26/9 - 30/9
S27 3/10 - 7/10
S28 10/10 - 14/10
S29 17/10 - 21/10
S30 24/10 - 28/10
S31 31/10 - 4/11
S32 7/11  - 11/11
S33 14/11 - 18/11
S34 21/11 - 25/11
S35 28/11 - 2/12
S36 5/12  - 9/12
S37 12/12 - 16/12
S38 19/12 - 23/12
S39 26/12 - 30/12
S40 4/1  - 8/1
S41 11/1 - 15/11
S42 18/1 - 22/1
S43 25/1 - 29/1
S44 1/2  - 5/2
S45 8/2  - 12/2
S46 15/2 - 19/2
S47 22/2 - 26/2
S48 29/2 - 4/3
S49 7/3  - 11/3
S50 14/3 - 18/3
S51 21/3 - 25/3
S52 28/3 - 1/4