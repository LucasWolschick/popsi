package popsi.ast;

import java.util.List;
import java.util.Optional;
import popsi.Token;
import popsi.ast.Expression.Block;

public sealed interface Ast {
        // Programa -> Lista de Funções
        public static record Program(List<Function> functions, List<Rec> records) implements Ast {
        }

        // Função -> "fn" identificador (parametros)? -> tipo bloco
        public static record Function(
                        Token name, // Nome da função
                        List<Parameter> parameters, // Lista de parâmetros
                        Optional<Type> returnType, // Tipo de retorno
                        Block body // Corpo da função
        ) implements Ast {
        }

        // Record -> "record" identificador "{" campos "}"
        public static record Rec(
                        Token name,
                        List<Rec_field> fields) implements Ast {
        }

        // Parâmetro -> identificador : tipo
        public static record Parameter(Token name, Type type) implements Ast {
        }

        public static record Rec_field(Token name, Type type) implements Ast {
        }
}
