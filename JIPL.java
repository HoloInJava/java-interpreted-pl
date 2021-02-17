package fr.holo.interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

import fr.holo.interpreter.JIPL.Error.RuntimeError;
import fr.holo.interpreter.JIPL.Error.SyntaxError;
import fr.holo.interpreter.JIPL.Interpreter.BuildInFunction;
import fr.holo.interpreter.JIPL.Interpreter.BuildInObjectClass;
import fr.holo.interpreter.JIPL.Interpreter.Number;
import fr.holo.interpreter.JIPL.Interpreter.RTResult;
import fr.holo.interpreter.JIPL.Interpreter.StringValue;
import fr.holo.interpreter.JIPL.Interpreter.Value;
import fr.holo.interpreter.JIPL.Parser.BinaryOperation;
import fr.holo.interpreter.JIPL.Parser.BreakNode;
import fr.holo.interpreter.JIPL.Parser.CallNode;
import fr.holo.interpreter.JIPL.Parser.CaseDataNode;
import fr.holo.interpreter.JIPL.Parser.ContinueNode;
import fr.holo.interpreter.JIPL.Parser.ForNode;
import fr.holo.interpreter.JIPL.Parser.FunctionDefNode;
import fr.holo.interpreter.JIPL.Parser.IfNode;
import fr.holo.interpreter.JIPL.Parser.InstantiateNode;
import fr.holo.interpreter.JIPL.Parser.ListNode;
import fr.holo.interpreter.JIPL.Parser.NumberNode;
import fr.holo.interpreter.JIPL.Parser.ObjectDefNode;
import fr.holo.interpreter.JIPL.Parser.ParseResult;
import fr.holo.interpreter.JIPL.Parser.PointAccessNode;
import fr.holo.interpreter.JIPL.Parser.ReturnNode;
import fr.holo.interpreter.JIPL.Parser.StringNode;
import fr.holo.interpreter.JIPL.Parser.UnaryOperation;
import fr.holo.interpreter.JIPL.Parser.VarAccessNode;
import fr.holo.interpreter.JIPL.Parser.VarAssignNode;
import fr.holo.interpreter.JIPL.Parser.VarModifyNode;
import fr.holo.interpreter.JIPL.Parser.WhileNode;

public class JIPL {
	
	/*
	 * 
	 * Java Interpreted Programming Language  by  @HoloInJava
	 * Feel free to use it in your projects :)
	 * 
	 */
	
	private static final boolean debug = false, performance = false;
	
	private static boolean stop = false;
	private static boolean tokenError = false;
	
	private static Scanner scn = new Scanner(System.in);
	
	public static void stop() { stop = true; }
	
	public static RTResult run(File file, Context context) {
		try {
			ArrayList<String> lines = new ArrayList<String>();
			BufferedReader br = new BufferedReader(new FileReader(file));
			while(true) {
				String s = br.readLine();
				if(s == null) break;
				lines.add(s);
			}
			br.close();
			return run(lines.toArray(new String[lines.size()]), context);
		} catch (IOException e) { e.printStackTrace(); }
		return null;
	}
	
	public static RTResult run(ArrayList<String> lines, Context context) { return run(lines.toArray(new String[lines.size()]), context); }
	
	public static RTResult run(String[] lines, Context context) { return run(String.join("\n", lines), context); }
	
	public static RTResult run(String lines, Context context) {
		ParseResult pr = getParseResult(lines);
		
		if(pr.error != null) {
			pr.error.call();
			return null;
		}
		
		Interpreter in = new Interpreter();
		
		long m1 = System.currentTimeMillis();
		
		RTResult output = (RTResult) in.visit(pr.node, context);
		if(output.error != null) output.error.call();
		
		if(debug) System.out.println(output);
		
		if(performance) System.out.println(System.currentTimeMillis()-m1 + " millis.");
		
		return output;
	}
	
	public static RTResult run(ParseResult pr, Context context) {
		Interpreter in = new Interpreter();
		RTResult output = (RTResult) in.visit(pr.node, context);
		if(output.error != null) output.error.call();
		return output;
	}
	
	public static ParseResult getParseResult(ArrayList<String> lines) { return getParseResult(lines.toArray(new String[lines.size()])); }
	
	public static ParseResult getParseResult(String[] lines) { return getParseResult(String.join("\n", lines)); }
	
	public static ParseResult getParseResult(String lines) {
		stop = false;
		tokenError = false;
		if(lines.isEmpty()) return null;
		
		if(debug) System.out.println("Running " + lines);
		
		ArrayList<Token> tokens = Lexer.getTokens(lines);
		if(debug) for(Token t:tokens) System.out.println("Lexer: "+t);
		if(tokenError) return null;
		
		ParseResult pr = (ParseResult) new Parser(tokens).parse();
		
		return pr;
	}
	
	public static final Context getGlobalContext() {
		SymbolTable st = new SymbolTable(null);
		
		st.set("PI", new Number(3.1415927f));
		st.set("PHI", new Number(1.618034f));
		st.set("true", Number.TRUE);
		st.set("false", Number.FALSE);
		st.set("null", Number.NULL);
		
		addBuildInFunctions(st);
		addMathFunctions(st);
		
		st.set("Object", new BuildInObjectClass("Object") {
			protected Object generateObject(Context context, Value... args) {
				RTResult res = new RTResult();
				return res.success(Number.NULL);
			}
		});
		
		return new Context("<Global>", null, st);
	}
	
	private static void addBuildInFunctions(SymbolTable st) {
		st.set("print", new BuildInFunction("print", "text") {
			protected Object executeFunction(Context context, Value... args) { System.out.println(""+args[0]); return args[0]; }
		});
		st.set("wait", new BuildInFunction("wait", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) {
					try { Thread.sleep((long) ((Number)args[0]).value);
					} catch (InterruptedException e) { e.printStackTrace(); }
				} else return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'wait'", seq));
				return res.success(Number.NULL);
			}
		});
		st.set("input", new BuildInFunction("input") {
			protected Object executeFunction(Context context, Value... args) {
				String str = scn.next();
				return new StringValue(str);
			}
		});
		st.set("inputNumber", new BuildInFunction("inputNumber") {
			protected Object executeFunction(Context context, Value... args) {
				int n = scn.nextInt();
				return new Number(n);
			}
		});
	}

	private static void addMathFunctions(SymbolTable st) {
		st.set("sin", new BuildInFunction("sin", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.sin(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'sin'", seq));
			}
		});
		st.set("cos", new BuildInFunction("cos", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.cos(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'cos'", seq));
			}
		});
		st.set("abs", new BuildInFunction("abs", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.abs(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'abs'", seq));
			}
		});
		st.set("floor", new BuildInFunction("floor", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.floor(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'floor'", seq));
			}
		});
		st.set("ceil", new BuildInFunction("ceil", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.ceil(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'ceil'", seq));
			}
		});
		st.set("toRadians", new BuildInFunction("toRadians", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.toRadians(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'toRadians'", seq));
			}
		});
		st.set("toDegrees", new BuildInFunction("toDegrees", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				if(args[0] instanceof Number) return res.success(new Number((float)Math.toDegrees(((Number) args[0]).value)));
				return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'toRadians'", seq));
			}
		});
		st.set("random", new BuildInFunction("random") {
			protected Object executeFunction(Context context, Value... args) {
				return new Number((float)Math.random());
			}
		});
		st.set("randomBetween", new BuildInFunction("randomBetween", "min", "max") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				
				float[] values = new float[2];
				for(int i = 0; i < values.length; i++)
					if(args[i] instanceof Number) values[i] = ((Number) args[i]).value;
					else return res.failure(new Error.RuntimeError("Invalid argument type " + args[i] + " in '"+name+"'", null));
				
				float bnd = values[1]-values[0];
				if(bnd < 0) return res.success(new Number(0f));
				
				return res.success(new Number(values[0]+new Random().nextInt((int)bnd+1)));
			}
		});
		st.set("sqrt", new BuildInFunction("sqrt", "value") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				float[] values = new float[1];
				for(int i = 0; i < values.length; i++)
					if(args[i] instanceof Number) values[i] = ((Number) args[i]).value;
					else return res.failure(new Error.RuntimeError("Invalid argument type " + args[i] + " in '"+name+"'", null));
				return new Number((float)Math.sqrt(values[0]));
			}
		});
		st.set("distance", new BuildInFunction("distance", "x1", "y1", "x2", "y2") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				float[] values = new float[4];
				for(int i = 0; i < values.length; i++)
					if(args[i] instanceof Number) values[i] = ((Number) args[i]).value;
					else return res.failure(new Error.RuntimeError("Invalid argument type " + args[i] + " in '"+name+"'", null));
				return new Number((float)Math.sqrt((values[0]-values[2])*(values[0]-values[2])+(values[1]-values[3])*(values[1]-values[3])));
			}
		});
		st.set("modulo", new BuildInFunction("modulo", "value", "diviser") {
			protected Object executeFunction(Context context, Value... args) {
				RTResult res = new RTResult();
				float[] values = new float[2];
				for(int i = 0; i < values.length; i++)
					if(args[i] instanceof Number) values[i] = ((Number) args[i]).value;
					else return res.failure(new Error.RuntimeError("Invalid argument type " + args[i] + " in '"+name+"'", null));
				return new Number(values[0]%values[1]);
			}
		});
	}

	public abstract static class Error {
		private static final boolean show = debug;
		
		public static class IllegalCharError extends Error {
			
			public IllegalCharError(String text, Sequence seq) { super("Illegal Character Error", text, seq); if(show) System.err.println("> "+text); }

			public void call(String... args) {
				System.err.println(name + " : Illegal character '" + text + "'" + (seq!=null?" at " + seq.toString():""));
			}
			
		}
		
		public static class ExpectedCharError extends Error {

			public ExpectedCharError(String text, Sequence seq) { super("Expected Character Error", text, seq); if(show) System.err.println("> "+text); }

			public void call(String... args) {
				System.err.println(name + " : Expected character '" + text + "'" + (seq!=null?" at " + seq.toString():""));
			}
			
		}
		
		public static class SyntaxError extends Error {
			
			public SyntaxError(String text, Sequence seq) { super("Syntax Error", text, seq); if(show) System.err.println("> "+text); }

			public void call(String... args) {
				System.err.println(name + " : " + getText() + (seq!=null?" at " + seq.toString():""));
			}
			
		}
		
		public static class RuntimeError extends Error {

			public RuntimeError(String text, Sequence seq) { super("Runtime Error", text, seq); if(show) System.err.println("> "+text); }

			public void call(String... args) {
				System.err.println(name + " : " + text + (seq!=null?" at " + seq.toString():""));
			}
			
		}
		
		public static class Stop extends Error {

			public Stop(String text, Sequence seq) {
				super("Stop", text, seq);
			}

			public void call(String... args) {}
			
		}
		
		protected String name, text;
		protected Sequence seq;
		
		public Error(String name, String text, Sequence seq) {
			this.name = name;
			this.text = text;
			this.seq = seq;
		}
		
		public String getName() { return name; }
		public String getText() { return text; }
		public Sequence getSeq() { return seq; }
		
		public abstract void call(String... args);
		
	}
	
	public static class Lexer {
		
		public static final String DIGITS = "0123456789";
		public static final String LETTERS = "azertyuiopqsdfghjklmwxcvbnAZERTYUIOPQSDFGHJKLMWXCVBN";
		public static final String LEGAL_CHARS = LETTERS+DIGITS+"_";
		
		public static final String[] KEYWORDS = {"var", "and", "or", "not", "if", "elseif", "else", "for", "to", "by", "while", "function", "return", "continue", "break", "new", "object"};
		
		public static ArrayList<Token> getTokens(String text) {
			ArrayList<Token> list = new ArrayList<JIPL.Token>();
			char[] chars = text.toCharArray();
			int line = 0;
			for(int i = 0; i < chars.length; i++) {
				char c = chars[i];
				
				if(tokenError) return list;
				
				if(c == ' ') continue;
				else if(c == '\t') continue;
				else if(c == '\n') { line++; list.add(new Token(TokenType.NLINE, new Sequence(line, i, 1))); }
				else if(c == ';') list.add(new Token(TokenType.NLINE, new Sequence(line, i, 1)));
				else if(c == '(') list.add(new Token(TokenType.LPAREN, new Sequence(line, i, 1)));
				else if(c == ')') list.add(new Token(TokenType.RPAREN, new Sequence(line, i, 1)));
				else if(c == '[') list.add(new Token(TokenType.LSQUARE, new Sequence(line, i, 1)));
				else if(c == ']') list.add(new Token(TokenType.RSQUARE, new Sequence(line, i, 1)));
				else if(c == '.') list.add(new Token(TokenType.POINT, new Sequence(line, i, 1)));
				else if(c == '{') list.add(new Token(TokenType.LBRA, new Sequence(line, i, 1)));
				else if(c == '}') list.add(new Token(TokenType.RBRA, new Sequence(line, i, 1)));
				else if(c == '+') list.add(new Token(TokenType.PLUS, new Sequence(line, i, 1)));
				else if(c == '-') list.add(new Token(TokenType.MINUS, new Sequence(line, i, 1)));
				else if(c == '*') list.add(new Token(TokenType.MULT, new Sequence(line, i, 1)));
				else if(c == '/') list.add(new Token(TokenType.DIV, new Sequence(line, i, 1)));
				else if(c == ':') list.add(new Token(TokenType.COLON, new Sequence(line, i, 1)));
				else if(c == ',') list.add(new Token(TokenType.COMMAS, new Sequence(line, i, 1)));
				else if(c == '^') list.add(new Token(TokenType.POW, new Sequence(line, i, 1)));
				else if(c == '!') i = _not_equals(chars, i, line, list);
				else if(c == '=') i = _comparator(chars, i, TokenType.DOUBLE_EQUALS, TokenType.EQUALS, line, list);			
				else if(c == '<') i = _comparator(chars, i, TokenType.LESS_EQUALS, TokenType.LESS, line, list);
				else if(c == '>') i = _comparator(chars, i, TokenType.GREATER_EQUALS, TokenType.GREATER, line, list);
				else if(c == '"') i = _string(chars, i, line, list);
				else if(c == '#') i = _comment(chars, i, line, list);
				else if(DIGITS.contains(c+"")) 	i = _number(chars, i, line, list);
				else if(LETTERS.contains(c+"")) i = _identifier(chars, i, line, list);
				else {
					new Error.IllegalCharError(c+"", new Sequence(0, i, 1)).call();
					tokenError = true;
					return list;
				}
			}
			list.add(new Token(TokenType.END_OF_CODE, new Sequence(line, text.length(), 0)));
			return list;
		}
		
		private static int _comment(char[] chars, int i, int line, ArrayList<Token> list) {
			while(true) {
				i++;
				if(i >= chars.length || chars[i] == '\n') break;
			}
			return i;
		}

		private static int _number(char[] chars, int index, int line, ArrayList<Token> list) {
			TokenType tt = TokenType.INT;
			String str = "";
			for(int i = index; i < chars.length; i++) {
				char c = chars[i];
				if(DIGITS.contains(c+"")) str += c;
				else if(c == '.' && tt == TokenType.INT) {
					str += c;
					tt = TokenType.FLOAT;
				} else {
					list.add(new Token(tt, new Sequence(line, index, str.length()), str));
					return i-1;
				}
			}
			list.add(new Token(tt, new Sequence(line, index, str.length()), str));
			return chars.length;
		}
		
		private static int _string(char[] chars, int index, int line, ArrayList<Token> list) {
			String str = "";
			boolean escapingNext = false;
			
			HashMap<Character, String> map = new HashMap<Character, String>();
			map.put('n', "\n");
			map.put('t', "\t");
			map.put('\\', "\\");
			
			for(int i = index+1; i < chars.length; i++) {
				char c = chars[i];
				
				if(escapingNext) {
					str+=map.getOrDefault(c, c+"");
					escapingNext = false;
					continue;
				}
				
				if(c == '\\') {
					escapingNext = true;
					continue;
				}
				
				if(c == '"' || c == '\n') {
					list.add(new Token(TokenType.STRING, new Sequence(line, index, str.length()), str));
					return i;
				} else str+=c;
				escapingNext = false;
			}
			
			return chars.length;
		}
		
		private static int _identifier(char[] chars, int index, int line, ArrayList<Token> list) {
			String str = "";
			for(int i = index; i < chars.length; i++) {
				char c = chars[i];
				if(LEGAL_CHARS.contains(c+"")) str += c;
				else break;
			}
			for(String s:KEYWORDS) {
				if(str.equals(s)) {
					list.add(new Token(TokenType.KEYWORD, new Sequence(line, index, str.length()), str));
					return index+str.length()-1;
				}
			}
			
			list.add(new Token(TokenType.IDENTIFIER, new Sequence(line, index, str.length()), str));
			return index+str.length()-1;
		}
		
		private static int _not_equals(char[] chars, int index, int line, ArrayList<Token> list) {
			if(index < chars.length-1 && chars[index+1] == '=') {
				list.add(new Token(TokenType.NOT_EQUALS, new Sequence(line, index, 2)));
				return index+1;
			} else {
				new Error.IllegalCharError("!", new Sequence(line, index, 1)).call();
				tokenError = true;
				return index+1;
			}
		}
		
		private static int _comparator(char[] chars, int index, TokenType e, TokenType ne, int line, ArrayList<Token> list) {
			if(index < chars.length-1) {
				if(chars[index+1]=='=') {
					list.add(new Token(e, new Sequence(line, index, 2)));
					return index+1;
				}
				
				list.add(new Token(ne, new Sequence(line, index, 1)));
				return index;
			}
			list.add(new Token(ne, new Sequence(line, index, 1)));
			return index;
		}
	}
	
	public static class Parser {
		
		protected static class NumberNode {
			
			protected Token token;
			public NumberNode(Token token) { this.token = token; }
			
			public String toString() { return token.toString(); }
			
		}
		
		protected static class StringNode {
			protected Token token;
			
			public StringNode(Token token) { this.token = token; }
			public String toString() { return token.toString(); }
		}
		
		protected static class BinaryOperation {
			
			protected Object leftNode, rightNode;
			protected Token operationToken;
			
			public BinaryOperation(Object leftNode, Token operationToken, Object rightNode) { 
				this.leftNode = leftNode;
				this.operationToken = operationToken;
				this.rightNode = rightNode;
			}
			
			public String toString() { return "("+leftNode+", "+operationToken+", "+rightNode+")"; }
			
		}
		
		protected static class UnaryOperation {
			
			protected Token operationToken;
			protected Object node;
			
			public UnaryOperation(Token operationToken, Object node) {
				this.operationToken = operationToken;
				this.node = node;
			}
			
			public String toString() { return "(" + operationToken + ", " + node + ")"; }
			
		}
		
		protected static class IfNode {
			
			protected ArrayList<CaseDataNode> cases;
			protected CaseDataNode else_case;
			
			public IfNode(ArrayList<CaseDataNode> cases, CaseDataNode else_case) {
				this.cases = cases;
				this.else_case = else_case;
			}
			
			public String toString() { return "(" + cases + " >> " + else_case + ")"; }
			
		}
		
		protected static class VarAssignNode {
			
			protected Token name;
			protected Object expression;
			
			public VarAssignNode(Token name, Object expression) {
				this.name = name;
				this.expression = expression;
			}
			
			public String toString() { return "Assign::"+name+"::"+expression; }
		}
		
		protected static class VarAccessNode {
			protected Token name;
			public VarAccessNode(Token name) { this.name = name; }
			public String toString() { return "Access::"+name; }
		}
		
		protected static class VarModifyNode {
			protected Token name;
			protected Object node;
			public VarModifyNode(Token name, Object node) {
				this.name = name;
				this.node = node;
			}
			
			public String toString() { return "Modify{"+name+" = "+node+"}"; }
		}
		
		protected static class ForNode {
			
			protected Token varName;
			protected Object start, end, step, body;
			protected boolean shouldReturnNull;
			
			public ForNode(Token varName, Object startNode, Object endNode, Object stepNode, Object bodyNode, boolean shouldReturnNull) {
				this.varName = varName;
				this.start = startNode;
				this.end = endNode;
				this.step = stepNode;
				this.body = bodyNode;
				this.shouldReturnNull = shouldReturnNull;
			}
			
			public String toString() { return "For"+"::"+body; }
			
		}
		
		protected static class WhileNode {
			
			protected Object condition, body;
			protected boolean shouldReturnNull;
			
			public WhileNode(Object condition, Object bodyNode, boolean shouldReturnNull) {
				this.condition = condition;
				this.body = bodyNode;
				this.shouldReturnNull = shouldReturnNull;
			}
			
			public String toString() { return "While::"+condition+"::"+body; }
			
		}
		
		protected static class FunctionDefNode {
			
			protected Token name;
			protected Object body;
			protected Token[] args;
			protected boolean shouldAutoReturn;
			
			public FunctionDefNode(Token name, Object bodyNode, boolean shouldAutoReturn, Token... args) {
				this.name = name;
				this.body = bodyNode;
				this.args = args;
				this.shouldAutoReturn = shouldAutoReturn;
			}
			
			public String toString() { return "Definition of " + name; }
			
		}
		
		protected static class CallNode {
			
			protected Object nodeToCall;
			protected Object[] args;
			
			public CallNode(Object nodeToCall, Object... args) {
				this.nodeToCall = nodeToCall;
				this.args = args;
			}
			
			public String toString() { return "Call of "+nodeToCall; }
			
		}
		
		protected static class ListNode {
			
			protected ArrayList<Object> elementNodes;

			public ListNode(ArrayList<Object> elementNodes) {
				this.elementNodes = elementNodes;
			}
			
			public String toString() {
				return "ListNode>"+elementNodes;
			}
			
		}
		
		protected static class PointAccessNode {
			
			protected Object[] nodes;
			
			protected PointAccessNode(Object... nodes) {
				this.nodes = nodes;
			}
			
			public String toString() { 
				String s = "";
				for(int i = 0; i < nodes.length; i++)
					s+=nodes[i];
				return "("+s+")";
			}
			
		}
		
		protected static class CaseDataNode {
			
			protected Object condition, statements;
			protected boolean shouldReturnNull;
			
			public CaseDataNode(Object condition, Object statements, boolean shouldReturnNull) {
				this.condition = condition;
				this.statements = statements;
				this.shouldReturnNull = shouldReturnNull;
			}
			
			public String toString() { return condition+"><"+statements; }
			
		}
		
		protected static class ReturnNode {
			
			protected Object toReturn;
			public ReturnNode(Object toReturn) {
				this.toReturn = toReturn;
			}
			
		}
		
		protected static class ContinueNode {}
		protected static class BreakNode {}
		
		protected static class ObjectDefNode {
			
			protected Token name;
			protected Token[] args;
			protected Object body;
			
			public ObjectDefNode(Token name, Token[] args, Object body) {
				this.name = name;
				this.args = args;
				this.body = body;
			}
			
			public String toString() { return name+">"+args; }
			
		}
		
		protected static class InstantiateNode {
			
			protected Object nodeToCall;
			protected Object[] args;
			
			public InstantiateNode(Object nodeToCall, Object... args) {
				this.nodeToCall = nodeToCall;
				this.args = args;
			}
			
			public String toString() { return "Instantiate of "+nodeToCall; }
			
		}
		
		public static class ParseResult {
			
			protected Error error = null;
			protected Object node = null;
			protected int last_registered_advance = 0, advance_count = 0, reverse_count = 0;
			
			public void register_advancement() {
				last_registered_advance = 1;
				advance_count++;
			}
			
			public Object register(Object node) {
				ParseResult pr = (ParseResult) node;
				last_registered_advance = pr.advance_count;
				advance_count += pr.advance_count;
				if(pr.error != null) error = pr.error;
				return pr.node;
			}
			
			public Object try_register(ParseResult node) {
				if(node.error != null) {
					reverse_count = node.advance_count;
					return null;
				}
				return register(node);
			}
			
			public ParseResult success(Object node) {
				this.node = node;
				return this;
			}
			
			public ParseResult failure(Error error) {
				this.error = error;
				return this;
			}
			
			public String toString() {
				if(error != null) error.call(); return node.toString();
			}
		}
		
		protected ArrayList<Token> tokens;
		protected Token currentToken;
		protected int index = -1;
		
		public Parser(ArrayList<Token> tokens) {
			this.tokens = tokens;
			advance();
		}

		private Token advance() {
			index++;
			updateCurrentToken();
			return currentToken;
		}
		
		private Token reverse(int amount) {
			index-=amount;
			if(debug) System.out.println("Parser: Reversing "+amount);
			updateCurrentToken();
			return currentToken;
		}
		
		private void advanceNewLines(ParseResult pr) {
			while(currentToken.matches(TokenType.NLINE)) {
				pr.register_advancement();
				advance();
			}
		}
		
		private void updateCurrentToken() {
			if(index < tokens.size() && index >= 0) currentToken = tokens.get(index);
		}
		
		private Object factor() {
			ParseResult pr = new ParseResult();
			
			advanceNewLines(pr);
			
			Token t = currentToken;
			
			if(t.matches(TokenType.INT, TokenType.FLOAT)) {
				pr.register_advancement();
				advance();
				return pr.success(new NumberNode(t));
			} if(t.matches(TokenType.STRING)) {
				pr.register_advancement();
				advance();
				return pr.success(new StringNode(t));
			} else if(t.matches(TokenType.IDENTIFIER)) {
				pr.register_advancement();
				advance();
				if(currentToken.matches(TokenType.EQUALS)) {
					pr.register_advancement();
					advance();
					
					Object o = pr.register(expression());
					if(pr.error != null) return pr;
					
					return pr.success(new VarModifyNode(t, o));
				}
				return pr.success(new VarAccessNode(t));
			} else if(t.matches(TokenType.LSQUARE)) {
				Object o = pr.register(list_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches(TokenType.PLUS, TokenType.MINUS)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object f = pr.register(call());
				if(pr.error != null) return pr;
				return pr.success(new UnaryOperation(t, f));
			} else if(t.matches("if", TokenType.KEYWORD)) {
				Object o = pr.register(if_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches("for", TokenType.KEYWORD)) {
				Object o = pr.register(for_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches("while", TokenType.KEYWORD)) {
				Object o = pr.register(while_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches("function", TokenType.KEYWORD)) {
				Object o = pr.register(function_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches("object", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object o = pr.register(object_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches("new", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object o = pr.register(new_expression());
				if(pr.error != null) return pr;
				return pr.success(o);
			} else if(t.matches(TokenType.LPAREN)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object ex = pr.register(expression());
				if(pr.error != null) return pr;
				if(currentToken.matches(TokenType.RPAREN)) {
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
					return pr.success(ex);
				} else return pr.failure(new Error.SyntaxError("Expected ')'", currentToken.getSeq()));
			}
			
			return pr.failure(new Error.SyntaxError("Unexpected <"+t+">", t.getSeq()));
		}
		
		private ParseResult statements() {
			if(debug) System.out.println("Parser: multi-lines statements");
			
			ParseResult pr = new ParseResult();
			ArrayList<Object> statements = new ArrayList<Object>();
			
			advanceNewLines(pr);
			
			Object stat = pr.register(statement());
			if(pr.error != null) return pr;
			statements.add(stat);
			
			boolean more = true;
			while(true) {
				int newline_count = 0;
				while(currentToken.matches(TokenType.NLINE)) {
					pr.register_advancement();
					advance();
					newline_count++;
				}
				if(newline_count == 0) more = false;
				
				if(!more) break;
				stat = pr.try_register(statement());
				if(stat == null) {
					reverse(pr.reverse_count);
					more = false;
					continue;
				}
				
				statements.add(stat);
			}
			
			if(debug) System.out.println("Parser: " + statements);
			
			return pr.success(new ListNode(statements));
		}
		
		private ParseResult statement() {
			ParseResult pr = new ParseResult();
			
			if(currentToken.matches("return", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object expr = pr.try_register(expression());
				if(expr == null) reverse(pr.reverse_count);
				return pr.success(new ReturnNode(expr==null?Number.NULL:expr));
			} else if(currentToken.matches("continue", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				return pr.success(new ContinueNode());
			} else if(currentToken.matches("break", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				return pr.success(new BreakNode());				
			}
			
			Object expr = pr.register(expression());
			if(pr.error != null)
				return pr;
			
			return pr.success(expr);
		}
		
		private ParseResult list_expression() {
			if(debug) System.out.println("Parser: List node");
			
			ParseResult pr = new ParseResult();
			
			ArrayList<Object> elementNodes = new ArrayList<Object>();
			
			if(!currentToken.matches(TokenType.LSQUARE))
				return pr.failure(new Error.SyntaxError("Expected '['", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			if(currentToken.matches(TokenType.RSQUARE)) {
				pr.register_advancement();
				advance();
			} else {
				elementNodes.add(pr.register(expression()));
				if(pr.error != null)
					return pr.failure(new Error.SyntaxError("Expected ']'", currentToken.getSeq()));
				while(currentToken.matches(TokenType.COMMAS)) {
					pr.register_advancement();
					advance();
					elementNodes.add(pr.register(expression()));
					if(pr.error != null) return pr;
				}
				advanceNewLines(pr);
				if(!currentToken.matches(TokenType.RSQUARE))
					return pr.failure(new Error.SyntaxError("Expected ']'", currentToken.getSeq()));
				pr.register_advancement();
				advance();
			}
			
			return pr.success(new ListNode(elementNodes));
		}
		
		private ParseResult if_expression() {
			ParseResult pr = new ParseResult();
			IfNode all_cases = new IfNode(new ArrayList<CaseDataNode>(), null);
			pr.register(if_expression_cases("if", all_cases));
			if(pr.error != null) return pr;
			return pr.success(new IfNode(all_cases.cases, all_cases.else_case));
		}
		
		private ParseResult if_expression_cases(String keyword, IfNode node) {
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches(keyword, TokenType.KEYWORD))
				return pr.failure(new Error.SyntaxError("Expected '"+keyword+"'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			Object condition = pr.register(expression());
			if(pr.error != null) return pr;
			
			if(currentToken.matches(TokenType.COLON)) {
//				return pr.failure(new Error.SyntaxError("Expected ':'", currentToken.getSeq()));
				pr.register_advancement();
				advance();
			}
			
			advanceNewLines(pr);
			
			if(currentToken.matches(TokenType.LBRA)) {
				pr.register_advancement();
				advance();
				Object statements = pr.register(statements());
				if(pr.error != null) return pr;
				node.cases.add(new CaseDataNode(condition, statements, true));
				
				if(currentToken.matches(TokenType.RBRA)) {
					pr.register_advancement();
					advance();
				} else {
					return pr.failure(new SyntaxError("Expected '}'", currentToken.getSeq()));
				}
				IfNode tempnode = (IfNode) pr.register(if_expression_bc());
				if(pr.error != null) return pr;
				node.cases.addAll(tempnode.cases);
				node.else_case = tempnode.else_case;
			} else {
				advanceNewLines(pr);
				Object expr = pr.register(statement());
				if(pr.error != null) return pr;
				node.cases.add(new CaseDataNode(condition, expr, false));
				
				IfNode tempnode = (IfNode) pr.register(if_expression_bc());
				if(pr.error != null) return pr;
				node.cases.addAll(tempnode.cases);
				node.else_case = tempnode.else_case;
			}
			
			return pr.success(node);
		}
		
		private Object if_expression_bc() {
			ParseResult pr = new ParseResult();
			IfNode node = new IfNode(new ArrayList<CaseDataNode>(), null);
			
			advanceNewLines(pr);
			
			boolean reverse = true;
			
			if(currentToken.matches("elseif", TokenType.KEYWORD)) {
				Object o = pr.register(if_expression_b());
				if(pr.error != null) return pr;
				IfNode tempnode = (IfNode) o;
				node.cases = tempnode.cases;
				node.else_case = tempnode.else_case;
				reverse = false;
			} else {
				Object o = pr.register(if_expression_c());
				if(pr.error != null) return pr;
				node.else_case = (CaseDataNode) o;
				if(o != null) reverse = false;
			}
			
			if(reverse) reverse(pr.advance_count);
			
			return pr.success(node);
		}

		private Object if_expression_b() {
			return if_expression_cases("elseif", new IfNode(new ArrayList<CaseDataNode>(), null));
		}
		
		private Object if_expression_c() {
			ParseResult pr = new ParseResult();
			Object else_case = null;
			
			if(currentToken.matches("else", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				
				if(currentToken.matches(TokenType.COLON)) {
//					return pr.failure(new Error.SyntaxError("Expected ':'", currentToken.getSeq()));
					pr.register_advancement();
					advance();
				}
				
				advanceNewLines(pr);
				
				if(currentToken.matches(TokenType.LBRA)) {
					pr.register_advancement();
					advance();
					
					Object statements = pr.register(statements());
					if(pr.error != null) return pr;
					
					else_case = new CaseDataNode(null, statements, true);
					
					if(currentToken.matches(TokenType.RBRA)) {
						pr.register_advancement();
						advance();
					} else return pr.failure(new Error.SyntaxError("Expected '}'", currentToken.getSeq()));
				} else {
					advanceNewLines(pr);
					Object expr = pr.register(statement());
					if(pr.error != null) return pr;
					else_case = new CaseDataNode(null, expr, false);
				}
			}
			
			return pr.success(else_case);
		}
		
		private ParseResult for_expression() {
			if(debug) System.out.println("Parser: for");
			
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches("for", TokenType.KEYWORD))
				return pr.failure(new Error.SyntaxError("Expected 'for'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			if(!currentToken.matches(TokenType.IDENTIFIER))
				return pr.failure(new Error.SyntaxError("Expected identifier", currentToken.getSeq()));
			
			Token varName = currentToken;
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			if(!currentToken.matches(TokenType.EQUALS))
				return pr.failure(new Error.SyntaxError("Expected '='", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			Object start = pr.register(expression());
			if(pr.error != null) return pr;
			
			advanceNewLines(pr);
			
			if(!currentToken.matches("to", TokenType.KEYWORD))
				return pr.failure(new Error.SyntaxError("Expected 'to'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			Object end = pr.register(expression());
			if(pr.error != null) return pr;
			
			Object by = null;
			
			advanceNewLines(pr);
			
			if(currentToken.matches("by", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				by = pr.register(expression());
				if(pr.error != null) return pr;
			}
			
			advanceNewLines(pr);
			
			if(currentToken.matches(TokenType.COLON)) {
//				return pr.failure(new Error.SyntaxError("Expected ':'", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
			}
			
			advanceNewLines(pr);
			
			if(currentToken.matches(TokenType.LBRA)) {
				pr.register_advancement();
				advance();

				Object body = pr.register(statements());
				if(pr.error != null) return pr;
				
				if(!currentToken.matches(TokenType.RBRA))
					return pr.failure(new Error.SyntaxError("Expected '}'", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
				
				return pr.success(new ForNode(varName, start, end, by, body, true));
			}
			
			Object body = pr.register(expression());
			if(pr.error != null) return pr;
			
			return pr.success(new ForNode(varName, start, end, by, body, false));
		}
		
		private ParseResult while_expression() {
			if(debug) System.out.println("Parser: while");
			
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches("while", TokenType.KEYWORD))
				return pr.failure(new Error.SyntaxError("Expected 'while'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			advanceNewLines(pr);
			
			Object condition = pr.register(expression());
			if(pr.error != null) return pr;
			
			if(currentToken.matches(TokenType.COLON)) {
				pr.register_advancement();
				advance();
			}
			
			if(currentToken.matches(TokenType.LBRA)) {
				pr.register_advancement();
				advance();
				
				Object body = pr.register(statements());
				if(pr.error != null) return pr;
				
				if(!currentToken.matches(TokenType.RBRA))
					return pr.failure(new Error.SyntaxError("Expected '}'", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
				
				return pr.success(new WhileNode(condition, body, true));
			}
			
			Object body = pr.register(statement());
			if(pr.error != null) return pr;
			
			return pr.success(new WhileNode(condition, body, false));
		}
		
		private ParseResult function_expression() {
			if(debug) System.out.println("Parser: function");
			
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches("function", TokenType.KEYWORD))
				return pr.failure(new Error.SyntaxError("Expected 'function'", currentToken.getSeq()));

			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			Token function_name = null;
			if(currentToken.matches(TokenType.IDENTIFIER)) {
				function_name = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				if(!currentToken.matches(TokenType.LPAREN))
					return pr.failure(new Error.SyntaxError("Expected '('", currentToken.getSeq()));
			} else {
				advanceNewLines(pr);
				if(!currentToken.matches(TokenType.LPAREN))
					return pr.failure(new Error.SyntaxError("Expected '(' or identifier", currentToken.getSeq()));
			}
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			ArrayList<Token> temp_tokens = new ArrayList<JIPL.Token>();
			
			if(currentToken.matches(TokenType.IDENTIFIER)) {
				temp_tokens.add(currentToken);
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				while(currentToken.matches(TokenType.COMMAS)) {
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
					if(!currentToken.matches(TokenType.IDENTIFIER))
						return pr.failure(new Error.SyntaxError("Expected identifier", currentToken.getSeq()));
					temp_tokens.add(currentToken);
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
				}
				if(!currentToken.matches(TokenType.RPAREN))
					return pr.failure(new Error.SyntaxError("Expected ',' or ')'", currentToken.getSeq()));
			} else {
				advanceNewLines(pr);
				if(!currentToken.matches(TokenType.RPAREN))
					return pr.failure(new Error.SyntaxError("Expected identifier or ')'", currentToken.getSeq()));
			}
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			if(currentToken.matches(TokenType.COLON)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				Object body = pr.register(expression());
				if(pr.error != null) return pr;
				
				return pr.success(new FunctionDefNode(function_name, body, true, temp_tokens.toArray(new Token[temp_tokens.size()])));
			} else {
				if(!currentToken.matches(TokenType.LBRA))
					return pr.failure(new SyntaxError("Expected ':' or '{'", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				Object body = pr.register(statements());
				if(pr.error != null) return pr;
				
				if(!currentToken.matches(TokenType.RBRA))
					return pr.failure(new SyntaxError("Function: expected '}'", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
				
				return pr.success(new FunctionDefNode(function_name, body, false, temp_tokens.toArray(new Token[temp_tokens.size()])));
			}
			
		}
		
		private ParseResult object_expression() {
			if(debug) System.out.println("Parser: object");
			
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches(TokenType.IDENTIFIER))
				return pr.failure(new Error.SyntaxError("Expected identifier for the object.", currentToken.getSeq()));
			
			Token name = currentToken;
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			if(!currentToken.matches(TokenType.LPAREN))
				return pr.failure(new Error.SyntaxError("Expected '('.", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			ArrayList<Token> args = new ArrayList<JIPL.Token>();
			if(currentToken.matches(TokenType.IDENTIFIER)) {
				args.add(currentToken);
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				while(currentToken.matches(TokenType.COMMAS)) {
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
					if(!currentToken.matches(TokenType.IDENTIFIER))
						return pr.failure(new Error.SyntaxError("Expected identifier", currentToken.getSeq()));
					args.add(currentToken);
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
				}
			}
			
			if(!currentToken.matches(TokenType.RPAREN))
				return pr.failure(new Error.SyntaxError("Expected ')'.", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			if(!currentToken.matches(TokenType.LBRA))
				return pr.failure(new SyntaxError("Expected ':' or '{'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			Object body = pr.register(statements());
			if(pr.error != null) return pr;
			
			if(!currentToken.matches(TokenType.RBRA))
				return pr.failure(new SyntaxError("Expected '}'", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			
			return pr.success(new ObjectDefNode(name, args.toArray(new Token[args.size()]), body));
		}
		
		private ParseResult new_expression() {
			ParseResult pr = new ParseResult();
			
			if(!currentToken.matches(TokenType.IDENTIFIER))
				return pr.failure(new Error.SyntaxError("Expected identifier", currentToken.getSeq()));
			
			Token name = currentToken;
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			if(!currentToken.matches(TokenType.LPAREN))
				return pr.failure(new Error.SyntaxError("Expected '('", currentToken.getSeq()));
			
			pr.register_advancement();
			advance();
			advanceNewLines(pr);
			
			ArrayList<Object> args = new ArrayList<Object>();
			
			if(currentToken.matches(TokenType.RPAREN)) {
				pr.register_advancement();
				advance();
			} else {
				Object expr1 = pr.register(expression());
				if(pr.error != null) return pr;
				args.add(expr1);
				
				while(currentToken.matches(TokenType.COMMAS)) {
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
					args.add(pr.register(expression()));
					if(pr.error != null) return pr;
				}
				
				if(!currentToken.matches(TokenType.RPAREN))
					return pr.failure(new Error.SyntaxError("Expected ')'", currentToken.getSeq()));
				pr.register_advancement();
				advance();
			}
			
			return pr.success(new InstantiateNode(new VarAccessNode(name), args.toArray()));
		}
		
		private ParseResult call() {
			ParseResult pr = new ParseResult();
			
			Object atom = pr.register(factor());
			if(pr.error != null) return pr;
			
			if(currentToken.matches(TokenType.LPAREN)) {
				if(debug) System.out.println("Parser: call_"+atom);
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				ArrayList<Object> args = new ArrayList<Object>();
				
				if(currentToken.matches(TokenType.RPAREN)) {
					pr.register_advancement();
					advance();
				} else {
					args.add(pr.register(expression()));
					if(pr.error != null)
						return pr.failure(new Error.SyntaxError("Expected ')'", currentToken.getSeq()));
					while(currentToken.matches(TokenType.COMMAS)) {
						pr.register_advancement();
						advance();
						advanceNewLines(pr);
						args.add(pr.register(expression()));
						if(pr.error != null) return pr;
					}
					if(!currentToken.matches(TokenType.RPAREN))
						return pr.failure(new Error.SyntaxError("Expected ')'", currentToken.getSeq()));
					pr.register_advancement();
					advance();
				}
				return pr.success(new CallNode(atom, args.toArray(new Object[args.size()])));
			}
			
			if(currentToken.matches(TokenType.POINT)) {
				ArrayList<Object> calls = new ArrayList<Object>();
				calls.add(atom);
				
				while(currentToken.matches(TokenType.POINT)) {
					pr.register_advancement();
					advance();
					advanceNewLines(pr);
					if(currentToken.matches(TokenType.LBRA)) {
						pr.register_advancement();
						advance();
						advanceNewLines(pr);
						Object obj = pr.register(statements());
						if(pr.error != null) return pr;
						if(!currentToken.matches(TokenType.RBRA))
							return pr.failure(new SyntaxError("Expected '}'", currentToken.getSeq()));
						pr.register_advancement();
						advance();
						advanceNewLines(pr);
						calls.add(obj);
					} else {
						Object obj = pr.register(call());
						if(pr.error != null) return pr;
						calls.add(obj);
					}
				}
				
				return pr.success(new PointAccessNode(calls.toArray()));
			}
			
			return pr.success(atom);
		}
		
		private ParseResult term() {
			ParseResult pr = new ParseResult();
			
			Object left = pr.register(call());
			if(pr.error != null) return pr;
			
			while(currentToken.matches(TokenType.MULT, TokenType.DIV)) {
				Token op = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object right = pr.register(call());
				if(pr.error != null) return pr;
				left = new BinaryOperation(left, op, right);
			}
			
			return pr.success(left);
		}
		
		private ParseResult comp_expression() {
			ParseResult pr = new ParseResult();
			
			if(currentToken.matches("not", TokenType.KEYWORD)) {
				Token tok = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object o = pr.register(comp_expression());
				if(pr.error != null) return pr;
				
				return pr.success(new UnaryOperation(tok, o));
			}
			
			Object o = pr.register(comp_arith());
			if(pr.error != null) return pr;
			
			return pr.success(o);
		}
		
		private ParseResult comp_binop() {
			ParseResult pr = new ParseResult();
			
			Object left = pr.register(comp_expression());
			if(pr.error != null) return pr;
			
			while(currentToken.matches("and", TokenType.KEYWORD) || currentToken.matches("or", TokenType.KEYWORD)) {
				Token op = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object right = pr.register(comp_expression());
				if(pr.error != null) return pr;
				left = new BinaryOperation(left, op, right);
			}
			
			return pr.success(left);
		}
		
		private ParseResult comp_arith() {
			ParseResult pr = new ParseResult();
			
			Object left = pr.register(arithmetic_expression());
			if(pr.error != null) return pr;
			
			while(currentToken.matches(TokenType.DOUBLE_EQUALS, TokenType.NOT_EQUALS, TokenType.LESS, TokenType.LESS_EQUALS, TokenType.GREATER, TokenType.GREATER_EQUALS)) {
				Token op = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object right = pr.register(arithmetic_expression());
				if(pr.error != null) return pr;
				left = new BinaryOperation(left, op, right);
			}
			
			return pr.success(left);
		}
		
		private ParseResult arithmetic_expression() {
			ParseResult pr = new ParseResult();
			
			Object left = pr.register(term());
			if(pr.error != null) return pr;
			
			while(currentToken.matches(TokenType.PLUS, TokenType.MINUS)) {
				Token op = currentToken;
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				Object right = pr.register(term());
				if(pr.error != null) return pr;
				left = new BinaryOperation(left, op, right);
			}
			
			return pr.success(left);
		}
		
		private ParseResult expression() {
			ParseResult pr = new ParseResult();
			
			if(currentToken.matches("var", TokenType.KEYWORD)) {
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				if(!currentToken.matches(TokenType.IDENTIFIER)) 
					return pr.failure(new Error.SyntaxError("Expected identifier for the variable", currentToken.getSeq()));
				
				Token vname = currentToken;
				
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				if(!currentToken.matches(TokenType.EQUALS))
					return pr.failure(new Error.SyntaxError("Expected '='", currentToken.getSeq()));
				
				pr.register_advancement();
				advance();
				advanceNewLines(pr);
				
				Object o = pr.register(expression());
				if(pr.error != null) return pr;
				
				return pr.success(new VarAssignNode(vname, o));
			}
			
			Object o = pr.register(comp_binop());
			if(pr.error != null) return pr;
			
			return pr.success(o);
		}
		
		public Object parse() {
			ParseResult pr = (ParseResult) statements();
//			if(pr.error != null)
//				pr.error.call();
//			else if(pr.error == null && currentToken.getType() != TokenType.END_OF_CODE)
//				return pr.failure(new Error.SyntaxError("Expected end... '+', '-', '*' or '/'", currentToken.getSeq()));
			return pr;
		}
		
	}
	
	public static class Interpreter {
		
		public static class Value {
			protected Context context;
			protected Sequence seq;
			
			protected Error.RuntimeError illegal_operation(Object obj) { return new RuntimeError("Illegal operation with " + obj, seq); }
			
			protected Object execute(Value... args) { System.err.println("No execution defined for " + this); return null; }
			public Value copy() { return this; }
			
			public Context getContext() { return context; }
			public Value setContext(Context context) { this.context = context; return this; }
			
			public Context generateContext(Context context) {
				if(this.context != null) return this.context;
				Context selfContext = new Context("<value>", context);
				selfContext.symbolTable.set("this", this);
				this.context = selfContext;
				return this.context;
			}
			
			protected Object add(Object obj) { return new StringValue(this+""+obj); }
			protected Object sub(Object obj) { return illegal_operation(obj); }
			protected Object mult(Object obj) { return illegal_operation(obj); }
			protected Object div(Object obj) { return illegal_operation(obj); }
			
			public Object _equals(Object obj) { return this.equals(obj)?Number.TRUE:Number.FALSE; }
			public Object _not_equals(Object obj) { return illegal_operation(obj); }
			public Object _less(Object obj) { return illegal_operation(obj); }
			public Object _greater(Object obj) { return illegal_operation(obj); }
			public Object _less_equals(Object obj) { return illegal_operation(obj); }
			public Object _greater_equals(Object obj) { return illegal_operation(obj); }
			public Object _and(Object obj) { return illegal_operation(obj); }
			public Object _or(Object obj) { return illegal_operation(obj); }
			
			public boolean isTrue() { return false; }
			
			public Object _not() { return !isTrue(); }
			
			public Sequence getSeq() { return seq; }
			public Value setSeq(Sequence seq) { this.seq = seq; return this; }
		}
		
		public static class Number extends Value {
			
			public static final Number NULL = new Number(0);
			public static final Number FALSE = new Number(0);
			public static final Number TRUE = new Number(1);
			
			protected float value;
			
			public Number(float value) { this.value = value; }
			public Number(Object value) { this.value = Float.parseFloat((String) value); }
			
			protected Object add(Object obj) {
				if(obj instanceof Number) return new Number(value+((Number)obj).value);
				else if(obj instanceof StringValue) return new StringValue(toString()+((StringValue)obj).value);
				else return illegal_operation(obj);
			}
			
			protected Object sub(Object obj) {
				if(obj instanceof Number) return new Number(value-((Number)obj).value); else return illegal_operation(obj);
			}
			
			protected Object mult(Object obj) {
				if(obj instanceof Number) return new Number(value*((Number)obj).value); else return illegal_operation(obj);
			}
			
			protected Object div(Object obj) {
				if(obj instanceof Number) {
					Number n = (Number)obj;
					if(n.value == 0) return new Error.RuntimeError("Division by zero", seq);
					return new Number(value/n.value);
				} else return illegal_operation(obj);
			}
			
			public Object _equals(Object obj) {
				if(obj instanceof Number) return new Number(isEqualTo(((Number)obj).value)?1:0); else return illegal_operation(obj);
			}
			
			public Object _not_equals(Object obj) {
				if(obj instanceof Number) return new Number(!isEqualTo(((Number)obj).value)?1:0); else return illegal_operation(obj);
			}
			
			public Object _less(Object obj) {
				if(obj instanceof Number) return new Number(value<((Number)obj).value?1:0); else return illegal_operation(obj);
			}
			
			public Object _greater(Object obj) {
				if(obj instanceof Number) return new Number(value>((Number)obj).value?1:0); else return illegal_operation(obj);
			}
			
			public Object _less_equals(Object obj) {
				if(obj instanceof Number) return new Number(value<=((Number)obj).value?1:0); else return illegal_operation(obj);
			}
			
			public Object _greater_equals(Object obj) {
				if(obj instanceof Number) return new Number(value>=((Number)obj).value?1:0); else return illegal_operation(obj);
			}
			
			public Object _and(Object obj) {
				if(obj instanceof Number) return new Number((isTrue()&&((Number)obj).isTrue())?1:0); else return illegal_operation(obj);
			}
			
			public Object _or(Object obj) {
				if(obj instanceof Number) return new Number((isTrue()||((Number)obj).isTrue())?1:0); else return illegal_operation(obj);
			}
			
			public boolean isTrue() { return !isEqualTo(0); }
			public Object _not() { return new Number(value==0?1:0); }
			
			public float getValue() { return value; }
			public void setValue(float value) { this.value = value; }
			
			public boolean isEqualTo(float x) { return Math.abs(value-x) < 0.00025f; }
			
			public String toString() { return (value%1==0?(int)value+"":value+""); }
			
			public Value copy() { return new Number(value).setContext(this.context); }
		}
		
		public static class StringValue extends Value {
			
			protected String value;
			
			public StringValue(String value) { this.value = value; }
			public StringValue(Object value) { this.value = (String) value; }
			
			protected Object add(Object obj) { return new StringValue(value+obj.toString()); }
			
			public Object _equals(Object obj) { return value.equalsIgnoreCase(obj+"")?Number.TRUE:Number.FALSE; }
			
			public boolean isTrue() { return value.length() > 0; }
			
			public Context generateContext(Context context) {
				if(this.context != null) return this.context;
				Context selfContext = new Context("<value>", context);
				selfContext.symbolTable.set("length", new Number(value.length()));
				selfContext.symbolTable.set("split", new BuildInFunction("split", "text") {
					protected Object executeFunction(Context context, Value... args) {
						String s = args[0].toString();
						String[] strs = s.equals(" ")?value.split("\\s+"):value.split(s);
						StringValue[] vls = new StringValue[strs.length];
						for(int i = 0; i < strs.length; i++)
							vls[i] = new StringValue(strs[i]);
						
						return new List(vls);
					}
				});
				selfContext.symbolTable.set("charAt", new BuildInFunction("charAt", "index") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						if(args[0] instanceof Number) {
							int index = (int) ((Number)args[0]).value;
							if(index < 0 || index >= value.length())
								return res.failure(new Error.RuntimeError("Index out of bounds " + index + ".", null));
							return res.success(new StringValue(""+value.charAt(index)));
						}
						return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'get'", seq));
					}
				});
				selfContext.symbolTable.set("substring", new BuildInFunction("sub", "start", "end") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						
						if(args[0] instanceof Number && args[1] instanceof Number) {
							int start = (int) ((Number)args[0]).value, end = (int) ((Number)args[1]).value;
							if(start < 0 || start > value.length())
								return res.failure(new Error.RuntimeError("Index out of bounds " + start + ".", null));
							if(end < 0 || end > value.length())
								return res.failure(new Error.RuntimeError("Index out of bounds " + end + ".", null));
							return res.success(new StringValue(value.substring(start, end)));
						}
						
						return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+"::"+args[1]+" is not allowed to the function 'get'", seq));
					}
				});
				selfContext.symbolTable.set("this", this);
				this.context = selfContext;
				return context;
			}
			
			public String toString() { return value; }
		}
		
		public static class BaseFunction extends Value {
			
			public String name;
			
			public BaseFunction(String name) {
				this.name = name==null?"<anonymous>":name;
			}
			
			protected Context generateNewContext() {
				Context new_context = new Context(name, context);
				return new_context;
			}
			
			protected Context generateNewContext(Context context) {
				Context new_context = new Context(name, context);
				return new_context;
			}
			
			protected Object check_args(String[] args_name, Value[] args) {
				RTResult res = new RTResult();
				if(args.length != args_name.length)
					return res.failure(new Error.RuntimeError("Incorrect number of argument have been passed in " + name, seq));
				return res.success(null);
			}
			
			protected void populate_args(String[] args_name, Value[] args, Context exec_context) {
				for(int i = 0; i < args.length; i++) {
					String arg_name = args_name[i];
					Value arg_value = args[i];
					arg_value.setContext(exec_context);
					exec_context.symbolTable.set(arg_name, arg_value);
				}
			}
			
			protected Object checkThenPopulate(String[] args_name, Value[] args, Context exec_context) {
				RTResult res = new RTResult();
				res.register(check_args(args_name, args));
				if(res.shouldReturn()) return res;
				populate_args(args_name, args, exec_context);
				return res.success(null);
			}
			
		}
		
		public static abstract class BuildInFunction extends BaseFunction {
			
			protected String[] args_name;
			public BuildInFunction(String name, String... args_name) {
				super(name);
				this.args_name = args_name;
			}
			
			public Object execute(Value... args) {
				RTResult res = new RTResult();
				Context con = generateNewContext();
				
				res.register(checkThenPopulate(args_name, args, con));
				if(res.shouldReturn()) return res;
				
				Object ret = res.register(executeFunction(con, args));
				if(res.shouldReturn()) return res;
				
				return res.success(ret);
			}
			
			protected abstract Object executeFunction(Context context, Value... args);
			
		}
		
		public static class Function extends BaseFunction {
			
			protected Object body_node;
			protected String[] args_name;
			protected boolean shouldAutoReturn;
			
			public Function(String name, Object body_node, String[] args_name, boolean shouldAutoReturn) {
				super(name);
				this.body_node = body_node;
				this.args_name = args_name;
				this.shouldAutoReturn = shouldAutoReturn;
			}
			
			public Object execute(Value... args) {
				RTResult res = new RTResult();
				Interpreter intepreter = new Interpreter();
				Context new_context = generateNewContext();
				
				res.register(checkThenPopulate(args_name, args, new_context));
				if(res.shouldReturn()) return res;
				
				Object value = res.register(intepreter.visit(body_node, new_context));
				
				if(res.shouldReturn() && res.returnValue == null) return res;
				
				Object ret = shouldAutoReturn?value:(res.returnValue!=null?res.returnValue:Number.NULL);
				return res.success(ret);
			}
			
			public Value copy() {
				Function func = new Function(name, body_node, args_name, shouldAutoReturn);
				func.setContext(context);
				func.setSeq(seq);
				return func;
			}
			
			public String toString() { return "<function "+name+">"; }
			
		}
		
		public static class List extends Value {
			protected ArrayList<Object> elements;
			public List(ArrayList<Object> elements) { this.elements = elements; }
			public List(Object[] elements) { this.elements = new ArrayList<Object>(); for(Object o:elements) this.elements.add(o); }
			
			public Value copy() {
				List list = new List(new ArrayList<Object>());
				list.elements.addAll(elements);
				list.context = this.context;
				return list;
			}
			
			public Object add(Object obj) {
				List list = (List) copy();
				if(obj instanceof List) list.elements.addAll(((List) obj).elements);
				else list.elements.add(obj);
				return list;
			}
			
			public Object mult(Object obj) {
				List list = (List) copy();
				list.elements.add(obj);
				return list;
			}
			
			public Context generateContext(Context context) {
				if(this.context != null) return this.context;
				
				Context selfContext = new Context("<value>", context);
				selfContext.symbolTable.set("add", new BuildInFunction("add", "element") {
					protected Object executeFunction(Context context, Value... args) {
						elements.add(args[0]);
						return args[0];
					}
				});
				selfContext.symbolTable.set("get", new BuildInFunction("get", "index") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						if(args[0] instanceof Number) {
							int index = (int) ((Number)args[0]).value;
							if(index < 0 || index >= elements.size())
								return res.failure(new Error.RuntimeError("Index out of bounds " + index, null));
							return res.success(elements.get(index));
						}
						return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'get'", seq));
					}
				});
				selfContext.symbolTable.set("set", new BuildInFunction("set", "index", "object") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						if(args[0] instanceof Number) {
							int index = (int) ((Number)args[0]).value;
							if(index < 0 || index >= elements.size())
								return res.failure(new Error.RuntimeError("Index out of bounds " + index, null));
							elements.set(index, args[1]);
							return args[1];
						}
						return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'get'", seq));
					}
				});
				selfContext.symbolTable.set("insert", new BuildInFunction("insert", "index", "object") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						if(args[0] instanceof Number) {
							int index = (int) ((Number)args[0]).value;
							if(index < 0 || index > elements.size())
								return res.failure(new Error.RuntimeError("Index out of bounds " + index, null));
							elements.add(index, args[1]);
							return args[1];
						}
						return res.failure(new Error.RuntimeError("Invalid argument type, "+args[0]+" is not allowed to the function 'get'", seq));
					}
				});
				selfContext.symbolTable.set("join", new BuildInFunction("join", "by") {
					protected Object executeFunction(Context context, Value... args) {
						String el = "";
						for(int i = 0; i < elements.size(); i++) {
							el+=elements.get(i);
							if(i!=elements.size()-1)
								el+=args[0];
						}
						return new RTResult().success(new StringValue(el));
					}
				});
				selfContext.symbolTable.set("clear", new BuildInFunction("clear") {
					protected Object executeFunction(Context context, Value... args) {
						elements.clear();
						return Number.NULL;
					}
				});
				selfContext.symbolTable.set("foreach", new BuildInFunction("foreach", "function") {
					protected Object executeFunction(Context context, Value... args) {
						RTResult res = new RTResult();
						
						BaseFunction fun = null;
						if(args[0] instanceof BaseFunction) fun = (BaseFunction) args[0];
						else return res.failure(new RuntimeError("Invalid argument type, "+args[0]+" is not allowed in the function 'foreach'", null));
						
						List l = new List(new ArrayList<Object>());
						for(Object el:elements) {
							Object o = res.register(fun.execute((Value) el));
							if(res.shouldReturn()) return res;
							l.elements.add(o);
						}
						
						return res.success(l);
					}
				});
				selfContext.symbolTable.set("size", new BuildInFunction("size") {
					protected Object executeFunction(Context context, Value... args) { return new Number(elements.size()); }
				});
				selfContext.symbolTable.set("this", this);
				this.context = selfContext;
				return selfContext;
			}
			
			public String toString() { return elements.toString(); }
			
		}
		
		public static class ObjectClass extends BaseFunction {
			
			protected String[] args_name;
			protected Object body;
			
			public ObjectClass(String name, String[] args_name, Object body) {
				super(name);
				this.args_name = args_name;
				this.body = body;
			}
			
			public Object execute(Context con, Value... args) {
				RTResult res = new RTResult();
				
				Interpreter interpreter = new Interpreter();
				Context new_context = generateNewContext(con);
				res.register(checkThenPopulate(args_name, args, new_context));
				if(res.error != null) return res;
				
				res.register(interpreter.visit(body, new_context));
				if(res.error != null) return res;
				
				ObjectValue obj = new ObjectValue(new_context);
				new_context.symbolTable.set("this", obj);
				new_context.symbolTable.set("type", this);
				return res.success(obj);
			}
			
		}
		
		public static abstract class BuildInObjectClass extends ObjectClass {

			public BuildInObjectClass(String name, String... args_name) {
				super(name, args_name, null);
			}
			
			public Object execute(Context con, Value... args) {
				RTResult res = new RTResult();
				
				Context new_context = generateNewContext(con);
				res.register(checkThenPopulate(args_name, args, new_context));
				if(res.error != null) return res;
				
				res.register(generateObject(new_context, args));
				if(res.error != null) return res;
				
				ObjectValue obj = new ObjectValue(new_context);
				new_context.symbolTable.set("this", obj);
				new_context.symbolTable.set("type", this);
				return res.success(obj);
			}
			
			protected abstract Object generateObject(Context context, Value... args);
			
		}
		
		public static class ObjectValue extends Value {
			protected Context selfContext;
			public ObjectValue(Context selfContext) { this.selfContext = selfContext; }
			
			public Context generateContext(Context context) { return selfContext; }
			
			public Value copy() { return this; }
			
			public String toString() { return selfContext.symbolTable.getSymbols().keySet()+""; }
		}
		
		public static class RTResult {
			
			protected Error error = null;
			protected Object value = null, returnValue = null;
			protected boolean shouldContinue = false, shouldBreak = false;
			
			public void reset() {
				error = null;
				value = null;
				returnValue = null;
				shouldContinue = false;
				shouldBreak = false;
			}
			
			public Object register(Object value) {
				if(value instanceof RTResult) {
					RTResult pr = (RTResult) value;
					if(pr.error != null) {
						error = pr.error;
						return this;
					}
					returnValue = pr.returnValue;
					shouldContinue = pr.shouldContinue;
					shouldBreak = pr.shouldBreak;
					return pr.value;
				}
				if(value instanceof Error) {
					error = (Error) value;
					return this;
				}
				return value;
			}
			
			public RTResult success(Object value) {
				reset();
				this.value = value;
				return this;
			}
			
			public RTResult success_return(Object value) {
				reset();
				returnValue = value;
				return this;
			}
			
			public RTResult success_continue() {
				reset();
				shouldContinue = true;
				return this;
			}
			
			public RTResult success_break() {
				reset();
				shouldBreak = true;
				return this;
			}
			
			public RTResult failure(Error value) {
				reset();
				this.error = value;
				return this;
			}
			
			public boolean shouldReturn() {
				return error != null || returnValue != null || shouldContinue || shouldBreak;
			}
			
			public String toString() {
				if(error != null) error.call();
				else if(value != null) return value.toString();
				return "";
			}
		}
		
		private Object visit(Object node, Context context) {
			if(debug) System.out.println("Intepreter: Visit " + node);
			if(stop) return new RTResult().failure(new Error.Stop("Stop.", null));
			
				 if(node instanceof NumberNode) 		return visitNumberNode((NumberNode) node, context);
			else if(node instanceof StringNode) 		return visitStringNode((StringNode) node, context);
			else if(node instanceof BinaryOperation) 	return visitBinaryOperation((BinaryOperation) node, context);
			else if(node instanceof UnaryOperation) 	return visitUnaryOperation((UnaryOperation) node, context);
			else if(node instanceof VarAccessNode)		return visitVarAccessNode((VarAccessNode) node, context);
			else if(node instanceof VarAssignNode) 		return visitVarAssignNode((VarAssignNode) node, context);
			else if(node instanceof IfNode) 			return visitIfNode((IfNode) node, context);
			else if(node instanceof VarModifyNode) 		return visitVarModifyNode((VarModifyNode) node, context);
			else if(node instanceof ForNode) 			return visitForNode((ForNode) node, context);
			else if(node instanceof WhileNode) 			return visitWhileNode((WhileNode) node, context);
			else if(node instanceof CallNode) 			return visitCallNode((CallNode) node, context);
			else if(node instanceof FunctionDefNode) 	return visitFunctionDefNode((FunctionDefNode) node, context);
			else if(node instanceof ListNode)			return visitListNode((ListNode) node, context);
			else if(node instanceof ReturnNode)			return visitReturnNode((ReturnNode) node, context);
			else if(node instanceof ContinueNode)		return visitContinueNode((ContinueNode) node, context);
			else if(node instanceof BreakNode)			return visitBreakNode((BreakNode) node, context);
			else if(node instanceof PointAccessNode)	return visitPointAccessNode((PointAccessNode) node, context);
			else if(node instanceof InstantiateNode)	return visitInstantiateNode((InstantiateNode) node, context);
			else if(node instanceof ObjectDefNode)		return visitObjectDefNode((ObjectDefNode) node, context);
			
//			if(debug)
			System.err.println("Intepreter: No visit for " + node + ".");
			return new RTResult().failure(new RuntimeError("Impossible interpretation.", null));
		}

		private Object visitNumberNode(NumberNode node, Context context) {
			return new RTResult().success(new Number(node.token.getValue()).setSeq(node.token.getSeq()));
		}
		
		private Object visitStringNode(StringNode node, Context context) {
			return new RTResult().success(new StringValue(node.token.getValue()).setSeq(node.token.getSeq()));
		}
		
		private Object visitVarAccessNode(VarAccessNode node, Context context) {
			RTResult res = new RTResult();
			String vname = (String) node.name.value;
			Object value = context.symbolTable.get(vname);
			
			if(value == null) return res.failure(new Error.RuntimeError(vname + " is not defined", node.name.getSeq()));
			
			return res.success(value);
		}
		
		private Object visitVarAssignNode(VarAssignNode node, Context context) {
			RTResult res = new RTResult();
			
			String vname = (String) node.name.value;
			Object value = res.register(visit(node.expression, context));
			
			if(res.shouldReturn()) return res;
			
			context.symbolTable.set(vname, value);
			
			return res.success(value);
		}
		
		private Object visitBinaryOperation(BinaryOperation node, Context context) {
			RTResult res = new RTResult();
			
			Object leftObj = res.register(visit(node.leftNode, context));
			if(res.shouldReturn()) return res;
			Value left = (Value) leftObj;
			
			Object rightObj = (Object)res.register(visit(node.rightNode, context));
			if(res.shouldReturn()) return res;
			Value right = (Value) rightObj;
			
				 if(node.operationToken.matches(TokenType.PLUS)) 	return res.success(left.add(right));
			else if(node.operationToken.matches(TokenType.MINUS))	return res.success(left.sub(right));
			else if(node.operationToken.matches(TokenType.MULT)) 	return res.success(left.mult(right));
			else if(node.operationToken.matches(TokenType.DIV)) {
				if(right instanceof Number)
					left.seq = node.operationToken.getSeq();
				Object o = left.div(right);
				if(o instanceof Error) return res.failure((Error) o);
				return res.success(o);
			} else if(node.operationToken.matches(TokenType.DOUBLE_EQUALS))return res.success(left._equals(right));
			else if(node.operationToken.matches(TokenType.NOT_EQUALS)) 	return res.success(left._not_equals(right));
			else if(node.operationToken.matches(TokenType.LESS)) 			return res.success(left._less(right));
			else if(node.operationToken.matches(TokenType.LESS_EQUALS)) 	return res.success(left._less_equals(right));
			else if(node.operationToken.matches(TokenType.GREATER)) 		return res.success(left._greater(right));
			else if(node.operationToken.matches(TokenType.GREATER_EQUALS)) return res.success(left._greater_equals(right));
			else if(node.operationToken.matches("and", TokenType.KEYWORD)) 	return res.success(left._and(right));
			else if(node.operationToken.matches("or", TokenType.KEYWORD))	return res.success(left._or(right));
			
			return res.failure(new Error.SyntaxError("Unknown symbol", node.operationToken.getSeq()));
		}
		
		private static final Number MINUS_ONE = new Number(-1);
		private Object visitUnaryOperation(UnaryOperation node, Context context) {
			RTResult res = new RTResult();
			
			if(debug) System.out.println("unary_node");
			Object obj = res.register(visit(node.node, context));
			if(res.shouldReturn()) return res;
			Number n = null;
			if(obj instanceof Number) n = (Number) obj; 
			else if(obj instanceof RTResult) n = (Number)((RTResult) obj).value;
			
			if(node.operationToken.matches(TokenType.MINUS)) n = (Number) n.mult(MINUS_ONE);
			else if(node.operationToken.matches("not", TokenType.KEYWORD)) n = (Number) n._not();
			
			return res.success(n);
		}
		
		private Object visitIfNode(IfNode node, Context context) {
			RTResult res = new RTResult();
			
			for(CaseDataNode cdn:node.cases) {
				Object condition = cdn.condition;
				Object expression = cdn.statements;
				
				Object condition_value = res.register(visit(condition, context));
				if(res.shouldReturn()) return res;
				
				if(((Number) condition_value).isTrue()) {
					Object expression_value = res.register(visit(expression, context));
					if(res.shouldReturn()) return res;
					
					return res.success(cdn.shouldReturnNull?Number.NULL:expression_value);
				}
			}
			
			if(node.else_case != null) {
				Object else_value = res.register(visit(node.else_case.statements, context));
				if(res.shouldReturn()) return res;
				return res.success(node.else_case.shouldReturnNull?Number.NULL:else_value);
			}
			
			return res.success(Number.NULL);
		}
		
		private Object visitVarModifyNode(VarModifyNode node, Context context) {
			RTResult res = new RTResult();
			String name = (String) node.name.value;
			Object value = res.register(visit(node.node, context));
			if(res.shouldReturn()) return res;
			
			if(name.equals("this") && context.symbolTable.parent != null) {
//				System.out.println("b");
//				Object obj = context.symbolTable.get("this");
//				
//				for(Map.Entry<String, Object> entry: context.symbolTable.symbols.entrySet()) {
//					if(obj.equals(entry.getValue())) {
//						System.out.println("c");
//						System.out.println(">"+entry.getKey()+":"+entry.getValue());
//						context.symbolTable.set(entry.getKey(), value);
//					}
//				}
			} else context.symbolTable.getSource(name).set(name, value);
			
			return res.success(value);
		}
		
		private Object visitForNode(ForNode node, Context context) {
			RTResult res = new RTResult();
			ArrayList<Object> elements = new ArrayList<Object>();
			
			Number start_value = (Number) res.register(visit(node.start, context));
			if(res.shouldReturn()) return res;
			
			Number end_value = (Number) res.register(visit(node.end, context));
			if(res.shouldReturn()) return res;
			
			Number step_value = new Number(start_value.value<end_value.value?1:-1);
			if(node.step != null) {
				step_value = (Number) res.register(visit(node.step, context));
				if(res.shouldReturn()) return res;
			}
			
			Number i = ((Number)start_value);
			
			while((step_value.value >= 0?i.value<((Number)end_value).value:i.value>((Number)end_value).value)) {
				context.symbolTable.set((String) node.varName.value, i);
				
				Object value = res.register(visit(node.body, context));
				if(res.shouldReturn() && !res.shouldContinue && !res.shouldBreak) return res;
				
				i = (Number) i.copy();
				i.value = i.value+step_value.value;
				
				if(res.shouldContinue) continue;
				if(res.shouldBreak) break;
				
				elements.add(value);
			}
			
			return res.success(node.shouldReturnNull?Number.NULL:new List(elements));
		}
		
		private Object visitWhileNode(WhileNode node, Context context) {
			RTResult res = new RTResult();
			ArrayList<Object> elements = new ArrayList<Object>();
			
			while(true) {
				Object condition = res.register(visit(node.condition, context));
				if(res.shouldReturn()) return res;
				
				if(!((Number) condition).isTrue()) break;
				
				Object value = res.register(visit(node.body, context));
				
				if(res.shouldReturn() && !res.shouldContinue && !res.shouldBreak) return res;
				
				if(res.shouldContinue) continue;
				if(res.shouldBreak) break;
				
				elements.add(value);
			}
			
			return res.success(node.shouldReturnNull?Number.NULL:new List(elements));
		}
		
		private Object visitFunctionDefNode(FunctionDefNode node, Context context) {
			RTResult res = new RTResult();
			String fname = node.name==null?null:(String)node.name.value;
			String[] args_name = new String[node.args.length];
			for(int i = 0; i < args_name.length; i++) args_name[i] = (String) node.args[i].value;
			
			Object function = new Function(fname, node.body, args_name, node.shouldAutoReturn).setContext(context).setSeq(node.name==null?null:node.name.getSeq());
			
			if(node.name != null)
				context.symbolTable.set(fname, function);
			
			return res.success(function);
		}
		
		private Object visitCallNode(CallNode node, Context context) {
			RTResult res = new RTResult();
			
			Object obj = res.register(visit(node.nodeToCall, context));
			if(res.shouldReturn()) return res;
			
			if(!(obj instanceof BaseFunction)) return obj;
			BaseFunction value_to_call = (BaseFunction) obj;
			
			ArrayList<Value> args_value = new ArrayList<Value>();
			for(Object a:node.args) {
				a = res.register(visit(a, context));
				if(a instanceof Value) args_value.add((Value) a);
				else if(a instanceof RTResult) args_value.add((Value) ((RTResult) a).value);
				if(res.shouldReturn()) return res;
			}
			
			Object exe = res.register(value_to_call.execute(args_value.toArray(new Value[args_value.size()])));
//			System.out.println(exe);
			if(res.shouldReturn()) return res;
			
			Object return_value = res.register(exe);
			if(res.shouldReturn()) return res;
			
			return res.success(return_value);
		}
		
		private Object visitListNode(ListNode node, Context context) {
			RTResult res = new RTResult();
			ArrayList<Object> elements = new ArrayList<Object>();
			for(Object o:node.elementNodes) {
				elements.add(res.register(visit(o, context)));
				if(res.shouldReturn()) return res;
			}
			return res.success(new List(elements));
		}
		
		private Object visitReturnNode(ReturnNode node, Context context) {
			RTResult res = new RTResult();
			
			Object value = Number.NULL;
			if(node.toReturn != null) {
				value = res.register(visit(node.toReturn, context));
				if(res.shouldReturn()) return res;
			}
			
			return res.success_return(value);
		}
		
		private Object visitContinueNode(ContinueNode node, Context context) { return new RTResult().success_continue(); }
		private Object visitBreakNode(BreakNode node, Context context) { return new RTResult().success_break(); }
		
		private Object visitPointAccessNode(PointAccessNode node, Context context) {
			RTResult res = new RTResult();
			Object currentReturn = Number.NULL;
			Context currentContext = context;
			for(Object index:node.nodes) {
				Object value = res.register(visit(index, currentContext));
				if(res.shouldReturn()) return res;
				
				if(value instanceof RTResult) currentContext = ((Value)((RTResult)value).value).generateContext(currentContext);
				else currentContext = ((Value)value).generateContext(currentContext);
				currentReturn = value;
			}
			return res.success(currentReturn);
		}
		
		private Object visitObjectDefNode(ObjectDefNode node, Context context) {
			RTResult res = new RTResult();
			
			String[] args_name = new String[node.args.length];
			for(int i = 0; i < args_name.length; i++)
				args_name[i] = (String) node.args[i].value;
			
			ObjectClass oc = new ObjectClass((String) node.name.value, args_name, node.body);
			context.symbolTable.set((String) node.name.value, oc);
			
			return res.success(oc);
		}
		
		private Object visitInstantiateNode(InstantiateNode node, Context context) {
			RTResult res = new RTResult();
			Object cl = res.register(visit(node.nodeToCall, context));
			if(res.shouldReturn()) return res;
			
			if(cl instanceof ObjectClass) {
				ArrayList<Value> args = new ArrayList<JIPL.Interpreter.Value>();
				
				for (Object obj:node.args) {
					obj = res.register(visit(obj, context));
					if(res.shouldReturn()) return res;
					else if(obj instanceof Value) args.add((Value) obj);
					else if(obj instanceof RTResult) args.add((Value)((RTResult) obj).value);
				}
				
				Object obj = res.register(((ObjectClass) cl).execute(context, args.toArray(new Value[args.size()])));
				if(res.shouldReturn()) return res;
				
				return res.success(obj);
			} else return res.failure(new Error.RuntimeError(cl + " is not an object.", null));
		}
		
	}
	
	public static class Context {
		
		public String displayName;
		public Context parent = null;
		public SymbolTable symbolTable;
		
		public Context(String displayName, Context parent) {
			this.displayName = displayName;
			this.parent = parent;
			this.symbolTable = new SymbolTable(parent==null?null:parent.symbolTable);
		}
		
		public Context(String displayName, Context parent, SymbolTable st) {
			this.displayName = displayName;
			this.parent = parent;
			this.symbolTable = st;
		}
		
		public void set(String name, Object value) { symbolTable.set(name, value); }
		public Object get(String name) { return symbolTable.get(name); }
		
	}
	
	public static class SymbolTable {
		
		protected HashMap<String, Object> symbols;
		protected SymbolTable parent = null;
		
		public SymbolTable(SymbolTable parent) {
			this.symbols = new HashMap<String, Object>();
			this.parent = parent;
		}
		
		public Object get(String name) {
			if(symbols.containsKey(name)) return symbols.get(name);
			if(parent != null) return parent.get(name);
			return Number.NULL;
		}
		
		public SymbolTable getSource(String name) {
			if(symbols.containsKey(name)) return this;
			if(parent != null) return parent.getSource(name);
			return this;
		}
		
		public void set(String name, Object value) { symbols.put(name, value); }
		public void remove(String name) { symbols.remove(name); }

		public HashMap<String, Object> getSymbols() { return symbols; }
		public void setSymbols(HashMap<String, Object> symbols) { this.symbols = symbols; }
		
	}
	
	public static class Sequence {
		private int line, offset, size;
		
		public Sequence(int line, int offset, int size) {
			this.line = line;
			this.offset = offset;
			this.size = size;
		}
		
		public int getLine() { return line; }
		public void setLine(int line) { this.line = line; }

		public int getOffset() { return offset; }
		public void setOffset(int offset) { this.offset = offset; }

		public int getSize() { return size; }
		public void setSize(int size) { this.size = size; }
		
		public String toString() {
//			if(line == 0) return "["+offset+"]";
			return "line "+line;
		}
		
		public String apply(String text) { return text.substring(offset, offset+size); }
		
	}
	
	public static final class Token {
		
		protected TokenType type;
		protected Object value;
		protected Sequence seq;
		
		public Token(TokenType type, Sequence seq, Object value) {
			this.type = type;
			this.seq = seq;
			this.value = value;
		}
		
		public Token(TokenType type, Sequence seq) {
			this.type = type;
			this.seq = seq;
		}
		
		public TokenType getType() { return type; }
		public void setType(TokenType type) { this.type = type; }
		
		public Sequence getSeq() { return seq; }
		public void setSeq(Sequence seq) { this.seq = seq; }

		public Object getValue() { return value; }
		public void setValue(Object value) { this.value = value; }
		
		public boolean matches(TokenType type) { return this.type == type; }
		public boolean matches(String value, TokenType type) { return this.type == type && this.value.equals(value); }
		public boolean matches(TokenType... types) { 
			for(TokenType tt:types)
				if(type == tt) return true;
			return false;
		}
		
		public String toString() { if(value == null) return type+""; return type+":"+value; }
		
	}
	
	public static enum TokenType {
		INT, FLOAT, STRING,
		PLUS, MINUS, MULT, DIV, POW,
		EQUALS, DOUBLE_EQUALS, NOT_EQUALS, LESS, GREATER, LESS_EQUALS, GREATER_EQUALS, LPAREN, RPAREN, LSQUARE, RSQUARE, LBRA, RBRA,
		COLON, COMMAS, POINT, NLINE,
		KEYWORD, IDENTIFIER,
		END_OF_CODE;
	}
	
}