package org.kframework.parser.concrete.disambiguate;

import java.util.ArrayList;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Ambiguity;
import org.kframework.kil.Term;
import org.kframework.kil.visitors.BasicTransformer;
import org.kframework.kil.visitors.exceptions.TransformerException;


public class BestFitFilter extends BasicTransformer {

	public BestFitFilter(GetFitnessUnitBasicVisitor gfubv) {
		super("Best fit filter");
		getFitnessUnit = gfubv;
	}

	private GetFitnessUnitBasicVisitor getFitnessUnit;

	public ASTNode transform(Ambiguity amb) throws TransformerException {

		// TODO: make this bottom up
		int maximum = getFitnessUnit(amb.getContents().get(0));

		// choose the maximums from the list of ambiguities
		java.util.List<Term> terms = new ArrayList<Term>();
		for (Term trm1 : amb.getContents()) {
			int temp = getFitnessUnit(trm1);
			if (temp > maximum)
				maximum = temp;
		}

		for (Term trm1 : amb.getContents()) {
			if (getFitnessUnit(trm1) == maximum)
				terms.add(trm1);
		}

		if (terms.size() == 1)
			return terms.get(0).accept(this);
		else
			amb.setContents(terms);

		return super.transform(amb);
	}

	private int getFitnessUnit(Term t) {
		GetFitnessUnitBasicVisitor fitnessVisitor = getFitnessUnit.getInstance();
		t.accept(fitnessVisitor);
		return fitnessVisitor.getScore();
	}
}