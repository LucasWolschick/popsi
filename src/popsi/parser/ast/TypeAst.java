package popsi.parser.ast;

import popsi.lexer.Token;

public sealed interface TypeAst {
    public record Named(Token name) implements TypeAst {
    }

    public record List(TypeAst elementType) implements TypeAst {
    }
}
