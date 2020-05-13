# Loads description #
This folder contains a number of files describing electric vehicles (EVs). This data is obtained from the Dundee city council.

The original data is changed by the following operations:
1. Set the maximum charging speed based on the type.
2. Round the arrival and starting times to 15 minute precision (floor).
3. Filter the data to contain only charging sessions of at least two hours and at most twelve hours. 
4. Filter the data to contain only charging sessions that require at least 5kWh and at most 29kWh.
5. Set the battery capacity to 30kWh.
6. Set a minimum required state of charge (SOC) to either (randomly) 85%, 90% or  95% of the battery's capacity.
7. Set the arrival SOC to the minimum required SOC - the charge amount.
8. Filter out the charging sessions with an arrival soc lower than 0 kWh.

The filtering and formatting of the data was performed with a script. The script is available on request.

This folder's setup is as follows:
* `difevs.ini` and `difevs4.ini`: Ten EVs with different starting times.  `difevs4.ini`'s starting date is April 4th, for the other it is April 1st.
* `dundeef.ini`: 50 EVs selected randomly from the filtered dundee data set.
* `dundeef2.ini`: 500 EVs selected randomly from the filtered dundee data set.
*  `same_evs4.ini`: A set of nine EVs that have the same arrival SOC, the same minimum required SOC, the same battery capacity, the same charging speed, and the same charging duration. The only difference is the start time of the session. All charging sessions take place in the week 04/04/2016 - 08/04/2016.
* `same_evs_95.ini`: similar to `same_evs4.ini`, but with 95 EVs throughout the whole year of 2016. All starting times are 19.00h until 07.00h. 

## References ##
1. Dundee City Council, Electric vehicle charging sessions, https://data.dundeecity.gov.uk/dataset/ev-charging-data, 2018, Accessed: 2018-03-21
