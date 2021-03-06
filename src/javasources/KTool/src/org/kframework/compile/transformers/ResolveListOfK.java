package org.kframework.compile.transformers;

import org.kframework.kil.*;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;

import java.util.ArrayList;
import java.util.Iterator;

/* TODO: andrei adds javadoc */
public class ResolveListOfK extends CopyOnWriteTransformer {

	public ResolveListOfK(org.kframework.kil.loader.Context context) {
		super("Resolve KList", context);
	}
	
	
	@Override
	public ASTNode transform(Syntax node) throws TransformerException {
		return node;
	}
	
	@Override
	public ASTNode transform(TermCons node) throws TransformerException {
		boolean change = false;
		ArrayList<Term> terms = new ArrayList<Term>();
		Production prod = context.conses.get(node.getCons());
		Iterator<Term> termIt = node.getContents().iterator();
		Term t;
		for (ProductionItem pitem : prod.getItems()) {
			if (pitem instanceof Terminal) continue;
			t = termIt.next();
			ASTNode resultAST = t.accept(this);
			if (resultAST != t) change = true;
			if (resultAST != null) {
				if (!(resultAST instanceof Term)) {
					GlobalSettings.kem.register(new KException(ExceptionType.ERROR, 
							KExceptionGroup.INTERNAL, 
							"Expecting Term, but got " + resultAST.getClass() + ".", 
							getName(), t.getFilename(), t.getLocation()));					
				}
				Term result = (Term) resultAST;
				if (pitem instanceof Sort 
						&& ((Sort)pitem).getName().equals(KSorts.KLIST)
						&& !t.getSort().equals(KSorts.KLIST)) {
					KList list = new KList();
					list.getContents().add(result);
					result = list;
					change = true;
				} 
				terms.add(result);
			}
		}
		if (change) {
			node = node.shallowCopy();
			node.setContents(terms);
		}
		return transform((Term) node);
	}

}
