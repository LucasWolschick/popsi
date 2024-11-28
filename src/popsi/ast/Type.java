package popsi.ast;

import popsi.Token;

public sealed interface Type {
    public record Named(Token name) implements Type {
    }

    public record List(Type elementType) implements Type {
    }
}
