package popsi.analysis.typed_ast;

import java.util.List;
import java.util.Optional;

import popsi.analysis.SymbolTable;
import popsi.analysis.SymbolTable.FunctionInfo;
import popsi.analysis.SymbolTable.Id;
import popsi.analysis.SymbolTable.RecordInfo;
import popsi.analysis.SymbolTable.TypeInfo;
import popsi.lexer.Token;
import popsi.parser.ast.TypeAst;

public sealed interface TypedAst {
        // Programa -> Lista de Funções
        public static record Program(List<Function> functions, List<Rec> records, SymbolTable table)
                        implements TypedAst {
        }

        // Função -> "fn" identificador (parametros)? -> tipo bloco
        public static record Function(
                        Token name, // Nome da função
                        List<Parameter> parameters, // Lista de parâmetros
                        Optional<TypeAst> returnType, // Tipo de retorno
                        TypedExpr.Block body, // Corpo da função
                        Id<FunctionInfo> function // Informações da função
        ) implements TypedAst {
        }

        // Record -> "record" identificador "{" campos "}"
        public static record Rec(
                        Token name,
                        List<RecField> fields,
                        Id<RecordInfo> record) implements TypedAst {
        }

        // Parâmetro -> identificador : tipo
        public static record Parameter(Token name, TypeAst typeAst, Id<TypeInfo> type) implements TypedAst {
        }

        public static record RecField(Token name, TypeAst typeAst, Id<TypeInfo> type) implements TypedAst {
        }
}
