import java.io.IOException;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import parser.TinyPiSLexer;
import parser.TinyPiSParser;

public class Compiler extends CompilerBase {
	void compileStmt(ASTNode ndx, Environment env) {
		if (ndx instanceof ASTCompoundStmtNode) {
			ASTCompoundStmtNode nd = (ASTCompoundStmtNode) ndx;
			for (ASTNode child: nd.stmts)
				compileStmt(child, env);
		} else if (ndx instanceof ASTAssignStmtNode) {
			ASTAssignStmtNode nd = (ASTAssignStmtNode) ndx;
			Variable var = env.lookup(nd.var);
			if (var == null)
				throw new Error("Undefined variable: "+nd.var);
			compileExpr(nd.expr, env);
		    if (var instanceof GlobalVariable) {
		    	GlobalVariable globalVar = (GlobalVariable) var;
		    	emitLDC(REG_R1, globalVar.getLabel());
		    	emitSTR(REG_DST, REG_R1, 0);
		    } else
		    	throw new Error("Not a global variable: "+nd.var);
		} else if (ndx instanceof ASTIfStmtNode) {
			ASTIfStmtNode nd = (ASTIfStmtNode) ndx;
			String elseLabel = freshLabel();
			String endLabel = freshLabel();
			compileExpr(nd.cond, env);
			emitRI("cmp", REG_DST, 0);
			emitJMP("beq", elseLabel);
			compileStmt(nd.thenClause, env);
			emitJMP("b", endLabel);
			emitLabel(elseLabel);
			compileStmt(nd.elseClause, env);
			emitLabel(endLabel);
		} else if (ndx instanceof ASTWhileStmtNode) {
			ASTWhileStmtNode nd = (ASTWhileStmtNode) ndx;
			String loopLabel = freshLabel();
			String endLabel = freshLabel();
			emitLabel(loopLabel);
			compileExpr(nd.cond, env);
			emitRI("cmp", REG_DST, 0);
			emitJMP("beq", endLabel);
			compileStmt(nd.stmt, env);
			emitJMP("b", loopLabel);
			emitLabel(endLabel);
		} else if (ndx instanceof ASTPrintStmtNode) {
			ASTPrintStmtNode nd = (ASTPrintStmtNode) ndx;
			compileExpr(nd.expr, env);
			emitCALL("print");
		} else
			throw new Error("Unknown expression: "+ndx);
	}
	void compileExpr(ASTNode ndx, Environment env) {
		if (ndx instanceof ASTBinaryExprNode) {
			ASTBinaryExprNode nd = (ASTBinaryExprNode) ndx;
			compileExpr(nd.lhs, env);
			emitPUSH(REG_R1);
			emitRR("mov", REG_R1, REG_DST);
			compileExpr(nd.rhs, env);
			if (nd.op.equals("|"))
				emitRRR("orr", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("&"))
				emitRRR("and", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("+"))
				emitRRR("add", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("-"))
				emitRRR("sub", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("*"))
				emitRRR("mul", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("/"))
				emitRRR("udiv", REG_DST, REG_R1, REG_DST);
			else
				throw new Error("Unknwon operator: "+nd.op);
			emitPOP(REG_R1);
		} else if (ndx instanceof ASTUnaryExprNode) {
			ASTUnaryExprNode nd = (ASTUnaryExprNode) ndx;
			compileExpr(nd.operand, env);
			if (nd.op.equals("-"))
				emitRRI("rsb", REG_DST, REG_DST, 0);   //0 - operand2 = -operand2
			else if (nd.op.equals("~"))
				emitRR("mvn", REG_DST, REG_DST);       //~operand
			else
				throw new Error("Unknwon operator: "+nd.op);
		} else if (ndx instanceof ASTNumberNode) {
			ASTNumberNode nd = (ASTNumberNode) ndx;
			emitLDC(REG_DST, nd.value);
		} else if (ndx instanceof ASTVarRefNode) {
			ASTVarRefNode nd = (ASTVarRefNode) ndx;
			Variable var = env.lookup(nd.varName);
			if (var == null)
				throw new Error("Undefined variable: "+nd.varName);
			if (var instanceof GlobalVariable) {
				GlobalVariable globalVar = (GlobalVariable) var;
				emitLDC(REG_DST, globalVar.getLabel());
				emitLDR(REG_DST, REG_DST, 0);
			} else
				throw new Error("Not a global variable: "+nd.varName);
		} else 
			throw new Error("Unknown expression: "+ndx);
	}
	
	void compile(ASTNode ast) {
		Environment env = new Environment();
		ASTProgNode prog = (ASTProgNode) ast;

		System.out.println("\t.section .data");
		System.out.println("\t@ 大域変数の定義");
		for (String varName: prog.varDecls) {
			if (env.lookup(varName) != null)
				throw new Error("Variable redefined: "+varName);
			GlobalVariable v = addGlobalVariable(env, varName);
			emitLabel(v.getLabel());
			System.out.println("\t.word 0");
		}
		if (env.lookup("answer") == null) {
			GlobalVariable v = addGlobalVariable(env, "answer");
			emitLabel(v.getLabel());
			System.out.println("\t.word 0");
		}

		System.out.println("hex:");
		System.out.println("\t.ascii \"00000000\\n\"");
		System.out.println("\t.equ hexlen, . - hex");

		System.out.println("\t.section .text");
		System.out.println("\t.global _start");
		System.out.println("_start:");
		System.out.println("\t@ 式をコンパイルした命令列");
		compileStmt(prog.stmt, env);
		System.out.println("\t@ EXITシステムコール");
		GlobalVariable v = (GlobalVariable) env.lookup("answer");
		emitLDC(REG_DST, v.getLabel());	// 変数 answer の値を r0 (終了コード)に入れる
		emitLDR("r0", REG_DST, 0);
		emitRI("mov", REG_R7, 1);			// EXIT のシステムコール番号
		emitI("swi", 0);
		
		subroutinePrint();
	}
	
	public void subroutinePrint() {
		System.out.println("print:");
		emitPUSH(REG_DST);
		emitPUSH(REG_R1);
		emitPUSH(REG_R2);
		emitPUSH(REG_R7);

		String loopLabel = freshLabel();
		String endLoop = freshLabel();
		String elseLabel = freshLabel();
		String endIf = freshLabel();
		emitLDC(REG_R7, "hex");
		emitRRI("add", REG_R7, REG_R7, 7);
		emitLabel(loopLabel);
		emitRI("cmp", REG_DST, 0);
		emitJMP("beq", endLoop);
		emitRI("mov", REG_R2, 16);
		emitRRR("udiv", REG_R1, REG_DST, REG_R2);
		emitRRR("mul", REG_R2, REG_R1, REG_R2);
		emitRRR("sub", REG_DST, REG_DST, REG_R2);
		emitRI("cmp", REG_DST, 10);
		emitJMP("bpl", elseLabel);
		emitRRI("add", REG_DST, REG_DST, 48);
		emitJMP("b", endIf);
		emitLabel(elseLabel);
		emitRRI("add", REG_DST, REG_DST, 55);
		emitLabel(endIf);
		emitSTRB(REG_DST, REG_R7, -1, true);
		emitRR("mov", REG_DST, REG_R1);
		emitJMP("b", loopLabel);
		emitLabel(endLoop);
		emitRI("mov", REG_R7, 4);
		emitRI("mov", REG_DST, 1);
		emitLDC(REG_R1, "hex");
		emitLDC(REG_R2, "hexlen");
		emitI("swi", 0);

		// 文字列の初期化
		loopLabel = freshLabel();
		endLoop = freshLabel();
		emitRI("mov", REG_DST, 48);
		emitRI("mov", REG_R2, 8);
		emitLabel(loopLabel);
		emitRI("cmp", REG_R2, 0);
		emitJMP("beq", endLoop);
		emitRRI("sub", REG_R2, REG_R2, 1);
		emitSTRB(REG_DST, REG_R1, REG_R2);
		emitJMP("b", loopLabel);
		emitLabel(endLoop);

		emitPOP(REG_R7);
		emitPOP(REG_R2);
		emitPOP(REG_R1);
		emitPOP(REG_DST);
		emitRET();
	}

	public static void main(String[] args) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(System.in);
		TinyPiSLexer lexer = new TinyPiSLexer(input);
		CommonTokenStream token = new CommonTokenStream(lexer);
		TinyPiSParser parser = new TinyPiSParser(token);
		ParseTree tree = parser.prog();
		ASTGenerator astgen = new ASTGenerator();
		ASTNode ast = astgen.translate(tree);
		Compiler compiler = new Compiler();
		compiler.compile(ast);
	}
}


/*

import java.io.IOException;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import parser.TinyPiELexer;
import parser.TinyPiEParser;

public class Compiler extends CompilerBase {
	void compileExpr(ASTNode ndx, Environment env) {
		if (ndx instanceof ASTBinaryExprNode) {
			ASTBinaryExprNode nd = (ASTBinaryExprNode) ndx;
			compileExpr(nd.lhs, env);
			emitPUSH(REG_R1);
			emitRR("mov", REG_R1, REG_DST);
			compileExpr(nd.rhs, env);
			if (nd.op.equals("+"))
				emitRRR("add", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("-"))
				emitRRR("sub", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("*"))
				emitRRR("mul", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("/"))
				emitRRR("udiv", REG_DST, REG_R1, REG_DST);
			else
				throw new Error("Unknwon operator: "+nd.op);
			emitPOP(REG_R1);
		} else if (ndx instanceof ASTNumberNode) {
			ASTNumberNode nd = (ASTNumberNode) ndx;
			emitLDC(REG_DST, nd.value);
		} else if (ndx instanceof ASTVarRefNode) {
			ASTVarRefNode nd = (ASTVarRefNode) ndx;
			Variable var = env.lookup(nd.varName);
			if (var == null)
				throw new Error("Undefined variable: "+nd.varName);
			if (var instanceof GlobalVariable) {
				GlobalVariable globalVar = (GlobalVariable) var;
				emitLDC(REG_DST, globalVar.getLabel());
				emitLDR(REG_DST, REG_DST, 0);
			} else
				throw new Error("Not a global variable: "+nd.varName);
		} else 
			throw new Error("Unknown expression: "+ndx);
	}
	
	void compile(ASTNode ast) {
		Environment env = new Environment();
		GlobalVariable vx = addGlobalVariable(env, "x");
		GlobalVariable vy = addGlobalVariable(env, "y");
		GlobalVariable vz = addGlobalVariable(env, "z");

		System.out.println("\t.section .data");
		System.out.println("\t@ 大域変数の定義");
		emitLabel(vx.getLabel());
		System.out.println("\t.word 1");
		emitLabel(vy.getLabel());
		System.out.println("\t.word 10");
		emitLabel(vz.getLabel());
		System.out.println("\t.word -1");
		System.out.println("\t.section .text");
		System.out.println("\t.global _start");
		System.out.println("_start:");
		System.out.println("\t@ 式をコンパイルした命令列");
		compileExpr(ast, env);
		System.out.println("\t@ EXITシステムコール");
		emitRI("mov", "r7", 1);   // EXIT のシステムコール番号
		emitI("swi", 0);
	}

	public static void main(String[] args) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(System.in);
		TinyPiELexer lexer = new TinyPiELexer(input);
		CommonTokenStream token = new CommonTokenStream(lexer);
		TinyPiEParser parser = new TinyPiEParser(token);
		ParseTree tree = parser.expr();
		ASTGenerator astgen = new ASTGenerator();
		ASTNode ast = astgen.translate(tree);
		Compiler compiler = new Compiler();
		compiler.compile(ast);
	}
}
*/
