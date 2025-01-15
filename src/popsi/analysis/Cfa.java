package popsi.analysis;

import popsi.analysis.Type.TypeAlgebra;
import popsi.analysis.typed_ast.TypedExpr;
import popsi.analysis.typed_ast.TypedStmt;

public class Cfa {
    private final SymbolTable table;

    public Cfa(SymbolTable table) {
        this.table = table;
    }

    public CfaResult ensureAllPathsReturnType(TypedExpr.Block expr, Type expected) {
        return visitExpr(expr, expected);
    }

    public enum CfaResult {
        RETURNED_TYPE, RETURNED_OTHER, CONTINUE;
    }

    // returns true if all paths return the expected type
    private CfaResult visitExpr(TypedExpr expr, Type expected) {
        return switch (expr) {
            case TypedExpr.Literal l -> CfaResult.CONTINUE;
            case TypedExpr.VariableExpression var -> CfaResult.CONTINUE;
            case TypedExpr.ListExpression list -> {
                for (TypedExpr element : list.elements()) {
                    var result = visitExpr(element, expected);
                    if (result != CfaResult.CONTINUE) {
                        yield result;
                    }
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.BinaryExpression bin -> {
                var left = visitExpr(bin.left(), expected);
                if (left != CfaResult.CONTINUE) {
                    yield left;
                }
                var right = visitExpr(bin.right(), expected);
                if (right != CfaResult.CONTINUE) {
                    yield right;
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.UnaryExpression un -> visitExpr(un.operand(), expected);
            case TypedExpr.FunctionCall call -> {
                for (TypedExpr.Argument arg : call.arguments()) {
                    var result = visitExpr(arg.value(), expected);
                    if (result != CfaResult.CONTINUE) {
                        yield result;
                    }
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.Argument arg -> visitExpr(arg.value(), expected);
            case TypedExpr.ListAccess access -> {
                var target = visitExpr(access.target(), expected);
                if (target != CfaResult.CONTINUE) {
                    yield target;
                }
                var place = visitExpr(access.place(), expected);
                if (place != CfaResult.CONTINUE) {
                    yield place;
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.RecAccess rec -> {
                var target = visitExpr(rec.target(), expected);
                if (target != CfaResult.CONTINUE) {
                    yield target;
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.ForExpression forExpr -> {
                var range = visitExpr(forExpr.range(), expected);
                if (range != CfaResult.CONTINUE) {
                    yield range;
                }
                var body = visitExpr(forExpr.body(), expected);
                if (body != CfaResult.CONTINUE) {
                    yield body;
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.IfExpression ifExpr -> {
                // check if all branches return the expected type
                // if either of them doesn't, then the whole expression doesn't
                var thenBranch = visitExpr(ifExpr.thenBranch(), expected);
                if (ifExpr.elseBranch().isPresent()) {
                    var elseBranch = visitExpr(ifExpr.elseBranch().get(), expected);
                    if (thenBranch == CfaResult.CONTINUE || elseBranch == CfaResult.CONTINUE) {
                        yield CfaResult.CONTINUE;
                    }
                    yield thenBranch == CfaResult.RETURNED_TYPE && elseBranch == CfaResult.RETURNED_TYPE
                            ? CfaResult.RETURNED_TYPE
                            : CfaResult.RETURNED_OTHER;
                } else {
                    yield thenBranch;
                }
            }
            case TypedExpr.WhileExpression whileExpr -> {
                var whileCond = visitExpr(whileExpr.condition(), expected);
                if (whileCond != CfaResult.CONTINUE) {
                    yield whileCond;
                }
                var body = visitExpr(whileExpr.body(), expected);
                if (body != CfaResult.CONTINUE) {
                    yield body;
                }
                yield CfaResult.CONTINUE;
            }
            case TypedExpr.ReturnExpression ret -> {
                if (visitExpr(ret.value(), expected) == CfaResult.RETURNED_TYPE) {
                    yield CfaResult.RETURNED_TYPE;
                }

                // else, compare the return type with the expected type
                var retType = table.typeDefinition(ret.type());
                if (retType.equals(expected)) {
                    yield CfaResult.RETURNED_TYPE;
                } else {
                    yield CfaResult.RETURNED_OTHER;
                }
            }
            case TypedExpr.DebugExpression debug -> CfaResult.CONTINUE;
            case TypedExpr.Block block -> {
                for (TypedStmt stmt : block.statements()) {
                    var result = visitStmt(stmt, expected);
                    if (result != CfaResult.CONTINUE) {
                        yield result;
                    }
                }
                if (block.lastStatement().isPresent()) {
                    var stmt = block.lastStatement().get();
                    var result = visitStmt(stmt, expected);
                    if (result != CfaResult.CONTINUE) {
                        yield result;
                    }
                    // get the type of the statement...
                    Type stmtType;
                    if (stmt instanceof TypedStmt.Declaration decl) {
                        var local = table.locals().get(decl.local()).get();
                        stmtType = table.typeDefinition(local.type());
                    } else if (stmt instanceof TypedStmt.ExpressionStatement exprStmt) {
                        stmtType = table.typeDefinition(exprStmt.type());
                    } else {
                        // unreachable
                        stmtType = null;
                    }

                    if (stmtType.equals(expected)) {
                        yield CfaResult.RETURNED_TYPE;
                    } else {
                        yield CfaResult.RETURNED_OTHER;
                    }
                }
                yield CfaResult.CONTINUE;
            }
        };
    }

    private CfaResult visitStmt(TypedStmt stmt, Type expected) {
        return switch (stmt) {
            case TypedStmt.Declaration decl -> {
                if (decl.value().isPresent()) {
                    yield visitExpr(decl.value().get(), expected);
                }
                yield CfaResult.CONTINUE;
            }
            case TypedStmt.ExpressionStatement expr -> {
                if (visitExpr(expr.expression(), expected) == CfaResult.RETURNED_TYPE) {
                    yield CfaResult.RETURNED_TYPE;
                }
                yield CfaResult.CONTINUE;
            }
        };
    }
}
