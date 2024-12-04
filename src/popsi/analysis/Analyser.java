package popsi.analysis;

import java.util.ArrayList;
import java.util.List;

import popsi.CompilerError;
import popsi.Result;
import popsi.parser.ast.Ast.*;
import popsi.parser.ast.Statement;
import popsi.parser.ast.Statement.*;

public class Analyser {
    public static Result<SymbolTable, List<CompilerError>> analyse(Program program) {
        var analyser = new Analyser();
        analyser.visit(program);
        if (analyser.errors.isEmpty()) {
            return new Result.Success<>(analyser.table);
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

    private void visit(Program program) {
        // on-demand semantic analysis
        for (var fn : program.functions()) {
            function(fn);
        }
    }

    private void function(Function fn) {
        // TODO: insert function into symbol table
        table.function(fn);

        // analyse function statements
        for (var stmt : fn.body().statements()) {
            statement(stmt);
        }
        if (fn.body().lastStatement().isPresent()) {
            statement(fn.body().lastStatement().get());
        }
    }

    private void statement(Statement stmt) {
        switch (stmt) {
            case Statement.Declaration decl:
                declaration(decl);
                break;
            case Statement.ExpressionStatement exprStmt:
                expressionStatement(exprStmt);
                break;
        }
    }

    private void declaration(Declaration decl) {

    }

    private void expressionStatement(ExpressionStatement stmt) {

    }
}
