package nl.tudelft.alg.fcc.problem;

import nl.tudelft.alg.fcc.utils.Utils;

public class OnlineResult {
	public Result evaluation;
	public Result[] evaluationList;
	public DecisionVariables[][] decisions;

	public OnlineResult(int nTimesteps) {
		decisions = new DecisionVariables[nTimesteps][1];
		evaluation = null;
		evaluationList = null;
	}

	public void addDecision(int t, DecisionVariables dec) {
		if (t > 0)
			dec = dec.padWithZeros(decisions[t - 1][0]);
		decisions[t][0] = dec;
	}

	public void concat(OnlineResult other) {
		evaluation.concat(other.evaluation);
		for (int t = 0; t < decisions.length; t++) {
			decisions[t] = Utils.concatArrays(decisions[t], other.decisions[t]);
		}
		for (int t = 0; t < evaluationList.length; t++) {
			evaluationList[t].concat(other.evaluationList[t]);
		}
	}

	public void addEvaluation(Result evaluation) {
		this.evaluation = evaluation;
	}

	public void printResultsToFile() {}

	public void addEvaluationList(Result[] resultsAll) {
		this.evaluationList = resultsAll;
	};
}
