package popsi.analysis.typed_ast;

import java.util.List;
import java.util.Optional;

import popsi.analysis.SymbolTable;
import popsi.analysis.Type;
import popsi.lexer.Token;
import popsi.parser.ast.Expr.Block;
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
                        Block body, // Corpo da função
                        Type type // Tipo da função
        ) implements TypedAst {
        }

        // Record -> "record" identificador "{" campos "}"
        public static record Rec(
                        Token name,
                        List<RecField> fields) implements TypedAst {
        }

        // Parâmetro -> identificador : tipo
        public static record Parameter(Token name, TypeAst typeAst, Type type) implements TypedAst {
        }

        public static record RecField(Token name, TypeAst typeAst, Type type) implements TypedAst {
        }
}
