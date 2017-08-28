import java.io.*;
import java.util.*;

/* Just a glance of what this compiler does. It generally parses the input codes and categorizes
the units into different nodes and doing name anaylysis, type check, etc. This is the last part of the
compiler which is generating the assembly codes (code gen). Generally, this method is like recurssion,
break up the input codes recurssively. AGAIN: THIS IS JUST PARTIAL OF THE WORK, THE ACTUAL
CODE TAKES 2825 LINES.*/

abstract class ASTnode { 
    // every subclass must provide an unparse operation
    abstract public void unparse(PrintWriter p, int indent);

    // this method can be used by the unparse methods to do indenting
    protected void doIndent(PrintWriter p, int indent) {
        for (int k=0; k<indent; k++) p.print(" ");
    }
	
	//public static List<ASTnode> dataNodes = new ArrayList<ASTnode>();
	abstract public void Codegen();
}


class ProgramNode extends ASTnode {
    public ProgramNode(DeclListNode L) {
        myDeclList = L;
    }

    /**
     * top-level nameAnalysis example.
     */
    public void nameAnalysis() {
		
		boolean hasMain = false;
		for(DeclNode node : myDeclList.getList()) {
			if(node instanceof FnDeclNode){
				FnDeclNode fnNode = (FnDeclNode) node;
				if(fnNode.Id().name().equals("main")) {
					hasMain = true;
				}
			}
		}
		
		if (!hasMain) {
			ErrMsg.fatal(0,0,"No main function");
		}
		
        SymTable symTab = new SymTable();
        myDeclList.nameAnalysis(symTab);
    }
    
    /**
     * top-level typeCheck example
     */
    public void typeCheck() {
        myDeclList.typeCheck();
    }
    /*unparse gives the variables types to prepare for type checking*/
    public void unparse(PrintWriter p, int indent) {
        myDeclList.unparse(p, indent);
    }
	public void Codegen(){}	
	public void generate(PrintWriter p) {
		Codegen.p = p;
		myDeclList.Codegen();
	}

    // 1 kid
    private DeclListNode myDeclList;
}


	//top-level codegenetator
	public void Codegen() {
		Codegen.p.println("\t.data");

                for (DeclNode node : myDecls) {
                        if (node instanceof VarDeclNode) {
                                ((VarDeclNode)node).Codegen();
                        }
                        if( node instanceof FnDeclNode) {
                                ((FnDeclNode)node).Codegen();
                        }
                }
	}
	
	public int size() {
		int size = 0;
		for(DeclNode node : myDecls){
			if(node instanceof VarDeclNode){
				VarDeclNode dnode = (VarDeclNode) node;
				size += dnode.size();
			}
		}

		return size;
	}
	//a helper method to determine offset in assembly
	public int setOffset(int offset) {
		for (DeclNode node : myDecls) {
			offset = node.setOffset(offset);
		}
		return offset;
	}
	
	public List<DeclNode> getList() {
		return myDecls;
	}
	
	public void printOffset() {
		for (DeclNode node : myDecls) {
			node.printOffset();
		}
	}

    // list of kids (DeclNodes)
    private List<DeclNode> myDecls;
}

class FormalsListNode extends ASTnode {
    public FormalsListNode(List<FormalDeclNode> S) {
        myFormals = S;
    }

    /**
     * nameAnalysis
     * Given a symbol table symTab, do:
     * for each formal decl in the list
     *     process the formal decl
     *     if there was no error, add type of formal decl to list
     */
    public List<Type> nameAnalysis(SymTable symTab) {
        List<Type> typeList = new LinkedList<Type>();
        for (FormalDeclNode node : myFormals) {
            SemSym sym = node.nameAnalysis(symTab);
            if (sym != null) {
                typeList.add(sym.getType());
            }
        }
        return typeList;
    }    
    
    /**
     * Return the number of formals in this list.
     */
    public int length() {
        return myFormals.size();
    }
    
    public void unparse(PrintWriter p, int indent) {
        Iterator<FormalDeclNode> it = myFormals.iterator();
        if (it.hasNext()) { // if there is at least one element
            it.next().unparse(p, indent);
            while (it.hasNext()) {  // print the rest of the list
                p.print(", ");
                it.next().unparse(p, indent);
            }
        } 
    }
	
	
	
	public void printOffset() {
		for (FormalDeclNode node : myFormals) {
			Codegen.p.println("# " + node.getId().name() + ", " + node.offset());
		}
	}
	
	public int size() {
		int size = 0;
		for(FormalDeclNode node : myFormals){
			size += node.size();
		}
		return size;
	}
	
	public int setOffset(int start) {
		int offset = start;
		for (FormalDeclNode node : myFormals) {
			offset += node.setOffset(offset);
		}
		return offset;
	}

    // list of kids (FormalDeclNodes)
    public List<FormalDeclNode> myFormals;
}

class FnBodyNode extends ASTnode {
    public FnBodyNode(DeclListNode declList, StmtListNode stmtList) {
        myDeclList = declList;
        myStmtList = stmtList;
    }

    /**
     * nameAnalysis
     * Given a symbol table symTab, do:
     * - process the declaration list
     * - process the statement list
     */
    public void nameAnalysis(SymTable symTab) {
        myDeclList.nameAnalysis(symTab);
        myStmtList.nameAnalysis(symTab);
    }    
 
    /**
     * typeCheck
     */
    public void typeCheck(Type retType) {
        myStmtList.typeCheck(retType);
    }    
          
    public void unparse(PrintWriter p, int indent) {
        myDeclList.unparse(p, indent);
        myStmtList.unparse(p, indent);
    }
	
	public void Codegen() {
		if (myDeclList.size() > 0) {
			Codegen.generateWithComment("subu", "local var", "$sp", "$sp", myDeclList.size()+"");
		}
		myStmtList.Codegen();
	}

	public int size() {
		return myDeclList.size() + myStmtList.size();
	}
	
	public int setOffset(int offset) {
		return myStmtList.setOffset(myDeclList.setOffset(offset));
	}
	
	public void printOffset(){
		myDeclList.printOffset();
	}
	
    // 2 kids
    private DeclListNode myDeclList;
    private StmtListNode myStmtList;
}

/****************************************************************************************/

//lower level methods
class WhileStmtNode extends StmtNode {
    public WhileStmtNode(ExpNode exp, DeclListNode dlist, StmtListNode slist) {
        myExp = exp;
        myDeclList = dlist;
        myStmtList = slist;
    }
    
    /**
     * nameAnalysis
     * Given a symbol table symTab, do:
     * - process the condition
     * - enter a new scope
     * - process the decls and stmts
     * - exit the scope
     */
    public void nameAnalysis(SymTable symTab) {
        myExp.nameAnalysis(symTab);
        symTab.addScope();
        myDeclList.nameAnalysis(symTab);
        myStmtList.nameAnalysis(symTab);
        try {
            symTab.removeScope();
        } catch (EmptySymTableException ex) {
            System.err.println("Unexpected EmptySymTableException " +
                               " in IfStmtNode.nameAnalysis");
            System.exit(-1);        
        }
    }
    
    /**
     * typeCheck
     */
    public void typeCheck(Type retType) {
        Type type = myExp.typeCheck();
        
        if (!type.isErrorType() && !type.isBoolType()) {
            ErrMsg.fatal(myExp.lineNum(), myExp.charNum(),
                         "Non-bool expression used as a while condition");        
        }
        
        myStmtList.typeCheck(retType);
    }
        
    public void unparse(PrintWriter p, int indent) {
        doIndent(p, indent);
        p.print("while (");
        myExp.unparse(p, 0);
        p.println(") {");
        myDeclList.unparse(p, indent+4);
        myStmtList.unparse(p, indent+4);
        doIndent(p, indent);
        p.println("}");
    }
	
	public int size() {
		return myDeclList.size() + myStmtList.size();
	}

	public int setOffset(int offset){
		offset = myDeclList.setOffset(offset);
		offset = myStmtList.setOffset(offset);
		return offset;
	}
	

	public void Codegen() {
		String conditionalLabel = Codegen.nextLabel() + "_while_start";
		String doneLabel = Codegen.nextLabel() + "_Done";
		Codegen.generateLabeled(conditionalLabel,"nop", "while");
		myExp.Codegen();
		Codegen.generateWithComment("beq", "while", "$t0", "$zero", doneLabel);
		myDeclList.Codegen();
		myStmtList.Codegen();
		Codegen.generate("j", conditionalLabel);
		Codegen.generateLabeled(doneLabel, "nop", "end while");
	}

    // 3 kids
    private ExpNode myExp;
    private DeclListNode myDeclList;
    private StmtListNode myStmtList;
}

