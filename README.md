# B-FELSA v1.1.0 #
Author: Koos van der Linden, 
Delft University of Technology   

Other contributors: Mathijs de Weerdt, Natalia Romero, Germán Morales-España

B-FELSA is a framework for benchmarking flexible electric load scheduling algorithms.
If you use this framework, please cite the following paper:

Koos van der Linden and Natalia Romero and Mathijs M. de Weerdt (2020). [Benchmarking Flexible Electric Loads Scheduling Algorithms under Market Price Uncertainty](https://arxiv.org/abs/2002.01246), _arXiv_ 2002.01246. 

## Solution methods ##
The framework contains a number of solution methods as explained in the mentioned paper. All solution methods are found in [this folder](src/main/java/nl/tudelft/alg/fcc/solution). The solution methods included are:
* `Direct.java`: The most simple solution method that just charges the electric load as soon as it connects until its requirements are met.
* `CurveApproxModel`: A solution method that is still in development. It uses a solution technique similar to dynamic programming. It computes a curve per Programme Time Unit (PTU) for the expected costs against the amount of charge required in that PTU and gives the best decision per required charge amount. Then the curves for the different PTUs are combined into one combined curve, and the solution is read from the curve.
* `SortommeModel.java` : an adaption of the MaxReg model from (Sortomme, 2010). The model is adapted to account for a reserve commitment deadline.
* `mip/DeterministicModel.java`: A deterministic mixed integer programming (MIP) model. Reserve price bids are determined by setting a desired acceptance probability. Adapted from (Sarker, 2016), see (van der Linden, 2018).
* `mip/StochasticModel.java` and `mip/CompactStochasticModel.java`: These two models do the same, but the compact model has less variables and less constraints, and the solution to the LP relaxation is often closer to the MIP solution. The model contains binary variables to model whether reserve bids are accepted or not in different scenario's. See (van der Linden, 2018)
* `FastImbalanceModel.java` and `mip/OptimalPriceModel.java`: These two models do the same thing. The FastImbalanceModel has some custom code to find the cheapest moments to buy energy in the day-ahead or imbalance market. The OptimalPriceModel does the same but builds on the MIP formulation also used for the deterministic and stochastic model.
* `mip/Heuristic.java`: A simple heuristic designed from experience for the Dutch market. This solution methods takes the OptimalPriceModel's solution and changes it slightly.
* `mip/LinApproxModel.java`: This model uses two linear approximation functions to find optimal reserve price bids. One function describes the acceptance probability of reserves for different reserve price bids. The other function gives the expected return (profit) for different reserve price bids.
* `lr/FlexibleLoadLRProblem.java`: A Lagrangian Relaxation approach. It utilizes the stochastic model.
* `efel/EFEL_P1.java`: This method clusters electric loads with similar charging speed to an 'equivalent flexible electric load' (EFEL), and solves for this smaller set of loads. The price bids from this relaxation are then used as a fixed input to the stochastic model to find a complete solution.

## Building from source ##
B-FELSA comes with a Maven project configuration file. In order to generate an executable jar file, you only need to execute the command `mvn package`.
B-FELSA uses the [MIPSolver project](https://github.com/AlgTUDelft/mipsolver). 

## How to run ##
To run B-FELSA, find the [main class](src/main/java/nl/tudelft/alg/fcc/main/App.java) and run it. The framework will be run based on the configuration file mentioned in this class. Config files can be found in the [data folder](data).

## References ##
1. Koos van der Linden and Natalia Romero and Mathijs M. de Weerdt, [Benchmarking Flexible Electric Loads Scheduling Algorithms under Market Price Uncertainty](https://arxiv.org/abs/2002.01246), _arXiv_ 2002.01246, 2020.
2. Koos van der Linden, Mathijs de Weerdt, and Germán Morales-España. Optimal non-zero price bids for EVs in energy and reserves markets using stochastic optimization. In Proceedings of the 15th International Conference on the European Energy Market (EEM), pages 574-578, IEEE, 2018.
3. Eric Sortomme and Mohamed A El-Sharkawi. Optimal charging strategies for unidirectional vehicle-to-grid. IEEE Transactions on Smart Grid, 2(1):131-138, 2010.
4. M. R. Sarker, Y. Dvorkin, and M. A. Ortega-Vazquez. Optimal Participation of an Electric Vehicle Aggregator in Day-Ahead Energy and Reserve Markets. IEEE Transactions on Power Systems, 31(5):3506-3515, September 2016.
