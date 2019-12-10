package nl.tudelft.alg.fcc.solution.mip;

import java.util.stream.Stream;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.fcc.model.Loads;

public class GridModel implements ConstraintGenerator {
	FlexibleLoadMIP mip;

	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		this.mip = mip;
		addGridConstraints();
	}

	protected void addGridConstraints() {
		Loads loads = mip.getLoads();
		int nGridPositions = loads.getNGridLines();
		for(int t=0; t<mip.nTimeSteps; t++) {
			for (int i = 0; i < mip.nScenarios; i++) {
				LinExp[] left = Stream.generate(() -> new LinExp()).limit(nGridPositions).toArray(LinExp[]::new);
				LinExp[] right = new LinExp[nGridPositions];
				for (int e = 0; e < loads.getNLoads(); e++) {
					int pos = loads.getGridPosition(e);
					left[pos].addLinExp(mip.getPc(e, t)).subtractLinExp(mip.getPd(e, t)).addLinExp(mip.getRd(e, t, i));
				}
				for (int p = 0; p < nGridPositions; p++) {
					if (left[p].getVariables().size() == 0) continue;
					right[p] = new LinExp(loads.getGridCapacity(t, p));
					mip.addConstraint(left[p], right[p], CMP.SMALLEREQ, "GRID_" + p + "_" + t + "_" + i);
				}
			}
		}
	}

}
