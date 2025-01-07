package popsi.analysis;

import java.util.ArrayList;
import java.util.List;

import popsi.CompilerError;
import popsi.Result;
import popsi.analysis.typed_ast.TypedExpr;
import popsi.analysis.typed_ast.TypedAst;
import popsi.analysis.typed_ast.TypedStmt;
import popsi.parser.ast.Expr;
import popsi.parser.ast.Ast;
import popsi.parser.ast.Stmt;

public class Analyser {
    public static Result<TypedAst.Program, List<CompilerError>> analyse(Ast.Program program) {
        var analyser = new Analyser();
        var typedProgram = analyser.program(program);
        if (analyser.errors.isEmpty()) {
            return new Result.Success<>(typedProgram);
        } else {
            return new Result.Error<>(analyser.errors);
        }
    }

    private List<CompilerError> errors;
    private SymbolTable table;

    private Analyser() {
        errors = new ArrayList<>();
        table = new SymbolTable();
    }

    private TypedAst.Program program(Ast.Program program) {
        // TODO
        throw new RuntimeException("Not implemented");
    }

    private TypedAst.Function function(Ast.Function function) {
        // TODO
        throw new RuntimeException("Not implemented");
    }

    private TypedAst.Rec rec(Ast.Rec rec) {
        // TODO
        throw new RuntimeException("Not implemented");
    }

    private TypedStmt statement(Stmt stmt) {
        // TODO
        throw new RuntimeException("Not implemented");
    }

    private TypedExpr expression(Expr expr) {
        // TODO
        throw new RuntimeException("Not implemented");
    }
}
