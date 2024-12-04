package popsi.interpreter;
// package popsi;

// import java.util.*;
// import popsi.CompilerError.ErrorType;
// import popsi.Token.TokenType;
// import popsi.ast.*;
// import popsi.ast.Ast.Function;
// import popsi.ast.Ast.Parameter;
// import popsi.ast.Ast.Program;
// import popsi.ast.Ast.Rec;
// import popsi.ast.Ast.Rec_field;
// import popsi.ast.Expression.BinaryExpression;
// import popsi.ast.Expression.Block;
// import popsi.ast.Expression.DebugExpression;
// import popsi.ast.Expression.ForExpression;
// import popsi.ast.Expression.FunctionCall;
// import popsi.ast.Expression.IfExpression;
// import popsi.ast.Expression.ListAccess;
// import popsi.ast.Expression.ListExpression;
// import popsi.ast.Expression.Literal;
// import popsi.ast.Expression.RangeExpression;
// import popsi.ast.Expression.ReturnExpression;
// import popsi.ast.Expression.UnaryExpression;
// import popsi.ast.Expression.VariableExpression;
// import popsi.ast.Expression.WhileExpression;
// import popsi.ast.Statement.Declaration;
// import popsi.ast.Statement.ExpressionStatement;
// import popsi.InterpreterContext;

// public class Interpreter {
// private final InterpreterContext context;

// public Interpreter() {
// this.context = new InterpreterContext();
// }

// public Object interpret(Program program) {
// // call the evaluate function for the Program node, this will recursively
// // evaluate all the nodes in the AST
// return evaluateProgram(program);
// }

// private Object evaluateProgram(Program program) {
// for (Rec rec : program.records()) {
// context.addRecord(rec.name().lexeme(), rec);
// }

// for (Function func : program.functions()) {
// context.addFunction(func.name().lexeme(), func);
// }
// return null;
// }

// private void evaluateStatement(Statement stmt) {
// if (stmt instanceof Declaration) {
// evaluateDeclaration((Declaration) stmt);
// } else if (stmt instanceof ExpressionStatement) {
// evaluateExpression(((ExpressionStatement) stmt).expression());
// }
// }

// private void evaluateDeclaration(Declaration stmt) {
// String name = stmt.name().lexeme();
// Object value = null;
// if (stmt.value().isPresent()) {
// value = evaluateExpression(stmt.value().get());
// }
// context.setVariable(name, value);
// }

// private Object evaluateExpression(Expression expr) {
// if (expr instanceof Literal) {
// return evaluateLiteral((Literal) expr);
// } else if (expr instanceof VariableExpression) {
// return evaluateVariable((VariableExpression) expr);
// } else if (expr instanceof BinaryExpression) {
// return evaluateBinaryExpression((BinaryExpression) expr);
// } else if (expr instanceof UnaryExpression) {
// return evaluateUnaryExpression((UnaryExpression) expr);
// } else if (expr instanceof FunctionCall) {
// return evaluateFunctionCall((FunctionCall) expr);
// } else if (expr instanceof IfExpression) {
// return evaluateIfExpression((IfExpression) expr);
// } else if (expr instanceof Block) {
// return evaluateBlock((Block) expr);
// } else if (expr instanceof RangeExpression) {
// return evaluateRangeExpression((RangeExpression) expr);
// }
// throw new RuntimeException("Unknown expression type: " +
// expr.getClass().getName());
// }

// private Object evaluateLiteral(Literal literal) {
// Token token = literal.value();
// switch (token.type()) {
// case INTEGER:
// return Integer.parseInt(token.lexeme());
// case FLOAT:
// return Float.parseFloat(token.lexeme());
// case STRING:
// return token.lexeme();
// case TRUE:
// return true;
// case FALSE:
// return false;
// default:
// throw new RuntimeException("Unknown literal type: " + token.type());
// }
// }

// private Object evaluateVariable(VariableExpression varExpr) {
// return context.getVariable(varExpr.name().lexeme());
// }

// private Object evaluateBinaryExpression(BinaryExpression binExpr) {
// Object left = evaluateExpression(binExpr.left());
// Object right = evaluateExpression(binExpr.right());
// Token operator = binExpr.operator();

// switch (operator.type()) {
// case PLUS:
// return (int) left + (int) right;
// case MINUS:
// return (int) left - (int) right;
// case STAR:
// return (int) left * (int) right;
// case SLASH:
// return (int) left / (int) right;
// case EQUAL_EQUAL:
// return left.equals(right);
// default:
// throw new RuntimeException("Unknown binary operator: " + operator);
// }
// }

// private Object evaluateUnaryExpression(UnaryExpression unaryExpr) {
// Object operand = evaluateExpression(unaryExpr.operand());
// Token operator = unaryExpr.operator();

// switch (operator.type()) {
// case MINUS:
// return -(int) operand;
// case BANG:
// return !(boolean) operand;
// default:
// throw new RuntimeException("Unknown unary operator: " + operator);
// }
// }

// /*
// * private Object evaluateFunctionCall(FunctionCall funcCall) {
// * Function function = context.getFunction(funcCall.target().toString());
// * InterpreterContext localEnv = new InterpreterContext(); // New scope for
// * function calls
// *
// * // Set parameters
// * List<Expression> args = funcCall.arguments();
// * for (int i = 0; i < args.size(); i++) {
// * Object argValue = evaluateExpression(args.get(i));
// * localEnv.defineVariable(args.get(i).toString(), argValue);
// * }
// *
// * // Execute the function body
// * Block body = function.getBody();
// * for (Statement stmt : body.getStatements()) {
// * evaluateStatement(stmt);
// * }
// *
// * return null; // Return value (if any) can be handled here.
// * }
// */
// private void evaluateIfExpression(IfExpression ifExpr) {
// boolean condition = (boolean) evaluateExpression(ifExpr.condition());
// if (condition) {
// evaluateBlock(ifExpr.thenBranch());
// } else if (ifExpr.elseBranch().isPresent()) {
// evaluateBlock(ifExpr.elseBranch().get());
// }
// }

// private void evaluateBlock(Block block) {
// for (Statement stmt : block.statements()) {
// evaluateStatement(stmt);
// }
// }

// private Object evaluateRangeExpression(RangeExpression rangeExpr) {
// // Implement range evaluation logic
// return null; // Placeholder
// }
// }
