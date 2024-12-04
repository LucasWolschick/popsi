package popsi.parser.ast;

import popsi.lexer.Token;

public sealed interface Type {
    public record Named(Token name) implements Type {
    }

    public record List(Type elementType) implements Type {
    }
}
