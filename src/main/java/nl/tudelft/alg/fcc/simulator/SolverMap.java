package nl.tudelft.alg.fcc.simulator;

import nl.tudelft.alg.MipSolverCore.IModel;
import nl.tudelft.alg.fcc.solution.CurveApproxModel;
import nl.tudelft.alg.fcc.solution.Direct;
import nl.tudelft.alg.fcc.solution.FastImbalanceModel;
import nl.tudelft.alg.fcc.solution.SolveFromFile;
import nl.tudelft.alg.fcc.solution.SortommeModel;
import nl.tudelft.alg.fcc.solution.efel.EFEL_P1;
import nl.tudelft.alg.fcc.solution.lr.FlexibleLoadLRModel;
import nl.tudelft.alg.fcc.solution.mip.CompactStochasticModel;
import nl.tudelft.alg.fcc.solution.mip.DeterministicModel;
import nl.tudelft.alg.fcc.solution.mip.Heuristic;
import nl.tudelft.alg.fcc.solution.mip.LinApproxModel;
import nl.tudelft.alg.fcc.solution.mip.StochasticModel;
import nl.tudelft.alg.fcc.solution.mip.OptimalPriceModel;

public enum SolverMap {
	D(Direct.class),
	CA(CurveApproxModel.class),
	H(Heuristic.class),
	IM(FastImbalanceModel.class),
	OPT(OptimalPriceModel.class),
	NM(LinApproxModel.class),
	Sor(SortommeModel.class),
	IRD(DeterministicModel.class),
	IRS(StochasticModel.class),
	IRSC(CompactStochasticModel.class),
	EFEL(EFEL_P1.class),
	LR(FlexibleLoadLRModel.class),
	File(SolveFromFile.class);
	
	Class<? extends IModel> type;
	
	SolverMap(Class<? extends IModel> type) {
      this.type = type;
	}
	
	Class<? extends IModel> getType() {
		return type;
	}
}
