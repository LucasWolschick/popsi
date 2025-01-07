package popsi.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import popsi.CompilerError;
import popsi.FilePosition;
import popsi.Result;
import popsi.CompilerError.ErrorType;
import popsi.analysis.typed_ast.TypedExpr;
import popsi.analysis.typed_ast.TypedAst;
import popsi.analysis.typed_ast.TypedStmt;
import popsi.lexer.Token;
import popsi.lexer.Token.TokenType;
import popsi.parser.ast.Expr;
import popsi.parser.ast.Ast;
import popsi.parser.ast.Stmt;
import popsi.parser.ast.TypeAst;

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
    private Environment environment;

    private Analyser() {
        errors = new ArrayList<>();
        table = new SymbolTable();
        environment = new Environment();
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
        switch (stmt) {
            // 1.1. O tipo de uma declaração é o tipo da expressão que ela contém.
            // 1.1.1. O tipo da declaração e o tipo da expressão devem ser compatíveis.
            // 1.2. O efeito de uma declaração é adicionar uma variável ao ambiente.
            // 1.2.1. A declaração sai de escopo ao final do bloco em que ela foi declarada.
            // 1.2.2. Caso já exista uma variável com o mesmo nome no ambiente, a existente
            // é ocultada, até que essa variável saia de escopo.
            // 1.3. O valor de uma declaração é o valor da expressão que ela contém, ou o
            // valor Unit, caso a expressão seja vazia.
            case Stmt.Declaration(Token name, TypeAst type, Optional<Expr> value):
                throw new RuntimeException("Not implemented");

            // 2.1. O tipo de uma expressão é o tipo do valor que ela representa.
            // 2.2. O valor de uma expressão é o valor que ela representa.
            case Stmt.ExpressionStatement(Expr expression): {
                var typedExpr = expression(expression);
                return new TypedStmt.ExpressionStatement(typedExpr, typedExpr.type());
            }
        }
    }

    private TypedExpr expression(Expr expr) {
        switch (expr) {
            // 1.1. O tipo de um literal inteiro é '{integer}'.
            // 1.2. O tipo de um literal float é '{float}'.
            // 1.3. O tipo de um literal string é 'str'.
            // 1.4. O tipo de um literal char é 'char'.
            // 1.5. O valor de um literal é ele mesmo.
            case Expr.Literal(Token value): {
                switch (value.type()) {
                    case TokenType.INTEGER:
                        return new TypedExpr.Literal(value, Type.I_LITERAL);
                    case TokenType.FLOAT:
                        return new TypedExpr.Literal(value, Type.F_LITERAL);
                    case TokenType.STRING:
                        return new TypedExpr.Literal(value, Type.STR);
                    case TokenType.CHAR:
                        return new TypedExpr.Literal(value, Type.CHAR);
                    default:
                        throw new RuntimeException("Unexpected token type: " + value.type());
                }
            }

            // 2.1. Uma variável precisa estar em escopo para ser usada.
            // 2.2. O tipo de uma variável é o tipo que foi atribuído a ela.
            // 2.3. O valor de uma variável é o valor que foi atribuído a ela no instante em
            // que ele é avaliado.
            case Expr.VariableExpression(Token name): {
                var entry = environment.get(name.lexeme());
                if (entry.isEmpty()) {
                    error(name, "uso de variável não declarada '" + name.lexeme() + "'");
                    return new TypedExpr.VariableExpression(name, Type.INVALID);
                }

                throw new RuntimeException("Not implemented");
            }

            // 3.1. O tipo de uma lista é o tipo dos elementos que ela contém.
            // 3.2. Todos os elementos de uma lista precisam ter tipos compatíveis.
            // 3.3. O valor de uma lista é a própria lista.
            case Expr.ListExpression(FilePosition position, List<Expr> elements): {
                if (elements.isEmpty()) {
                    // Não sabemos o tipo da lista vazia, então usamos o tipo desconhecido.
                    return new TypedExpr.ListExpression(position, List.of(), Type.UNKNOWN);
                }

                var first = expression(elements.get(0));
                var elementType = first.type();
                var typedElements = new ArrayList<TypedExpr>();
                for (var element : elements) {
                    var typedElement = expression(element);
                    // TODO: Não queremos igualdade estrita aqui, mas sim compatibilidade de tipos.
                    // Precisamos implementar um método `Type::compatible` para isso.
                    // Exemplos:
                    // - {integer} é compatível com u8, i8, u16, i16, u32, i32, u64, i64
                    // - {float} é compatível com f32, f64
                    // - [[], [1]] é compatível com [[{integer}]]
                    // Mudar para um método que não seja igualdade estrita.
                    // TODO: seria melhor colocar o erro na posição do elemento, não na posição da
                    // lista.
                    if (!typedElement.type().equals(elementType)) {
                        error(position, "todos os elementos de uma lista devem possuir o mesmo tipo");
                    }

                    typedElements.add(typedElement);
                }

                var type = new Type.Named("[]", List.of(elementType));

                return new TypedExpr.ListExpression(position, typedElements, type);
            }

            // TODO: definir o que é um lugar atribuível.
            // TODO: definir o que é um tipo compatível.
            // TODO: definir igualdade de valores.

            // 4.1. O tipo de uma operação binária é o tipo do resultado da operação.

            // 4.2. Os tipos dos operandos precisam ser compatíveis com os requeridos pela
            // operação.

            // 4.3. As operações são as seguintes:

            // 4.3.1. Atribuição: =
            // 4.3.1.1. O lado esquerdo de uma atribuição precisa ser um lugar atribuível.
            // 4.3.1.2. O tipo do lado direito precisa ser compatível com o tipo do lado
            // esquerdo.
            // 4.3.1.3. O efeito da atribuição é que o lugar atribuível passa a ter o valor
            // do lado direito.
            // 4.3.1.4. O valor da atribuição é o valor do lado direito.

            // 4.3.2. Módulo com atribuição: %=
            // 4.3.2.1. A operação a %= b é válida se e somente se a = a % b for válida.
            // 4.3.2.3. O tipo da operação é o tipo dos operandos.
            // 4.3.2.4. O valor da operação é a % b.
            // 4.3.2.5. O efeito da operação é que a passa a ter o valor a % b.

            // 4.3.3. Soma com atribuição: +=
            // 4.3.3.1. A operação a += b é válida se e somente se a = a + b for válida.
            // 4.3.3.3. O tipo da operação é o tipo dos operandos.
            // 4.3.3.4. O valor da operação é a % b.
            // 4.3.3.5. O efeito da operação é que a passa a ter o valor a % b.

            // 4.3.4. Subtração com atribuição: -=
            // 4.3.4.1. A operação a -= b é válida se e somente se a = a - b for válida.
            // 4.3.4.3. O tipo da operação é o tipo dos operandos.
            // 4.3.4.4. O valor da operação é a - b.
            // 4.3.4.5. O efeito da operação é que a passa a ter o valor a - b.

            // 4.3.5. Multiplicação com atribuição: *=
            // 4.3.5.1. A operação a *= b é válida se e somente se a = a * b for válida.
            // 4.3.5.3. O tipo da operação é o tipo dos operandos.
            // 4.3.5.4. O valor da operação é a * b.
            // 4.3.5.5. O efeito da operação é que a passa a ter o valor a * b.

            // 4.3.6. Divisão com atribuição: /=
            // 4.3.6.1. A operação a /= b é válida se e somente se a = a / b for válida.
            // 4.3.6.3. O tipo da operação é o tipo dos operandos.
            // 4.3.6.4. O valor da operação é a / b.
            // 4.3.6.5. O efeito da operação é que a passa a ter o valor a / b.

            // 4.3.7. Exponenciação com atribuição: ^=
            // 4.3.7.1. A operação a ^= b é válida se e somente se a = a ^ b for válida.
            // 4.3.7.3. O tipo da operação é o tipo dos operandos.
            // 4.3.7.4. O valor da operação é a ^ b.
            // 4.3.7.5. O efeito da operação é que a passa a ter o valor a ^ b.

            // 4.3.8. Intervalo: ..
            // 4.3.8.1. O tipo de um intervalo é o tipo dos elementos que ele contém.
            // 4.3.8.2. O tipo do lado esquerdo deve ser compatível com o tipo do lado
            // direito.
            // 4.3.8.3. O valor de um intervalo é o próprio intervalo.

            // 4.3.9. Ou: ||
            // 4.3.9.1. O tipo do operador é booleano.
            // 4.3.9.2. Os dois operandos precisam ser booleanos.
            // 4.3.9.3. O valor da operação é verdadeiro sse pelo menos um dos operandos for
            // verdadeiro.
            // 4.3.9.4. O operador possui curto-circuito. O operando à direita somente é
            // avaliado se o da esquerda for falso.

            // 4.3.10. E: &&
            // 4.3.10.1. O tipo do operador é booleano.
            // 4.3.10.2. Os dois operandos precisam ser booleanos.
            // 4.3.10.3. O valor da operação é verdadeiro sse ambos os operandos forem
            // verdadeiros.
            // 4.3.10.4. O operador possui curto-circuito. O operando à direita somente é
            // avaliado se o da esquerda for verdadeiro.

            // 4.3.11. Igual: ==
            // 4.3.11.1. O tipo do operador é booleano.
            // 4.3.11.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.11.3. O valor da operação é verdadeiro sse os valores dos operandos forem
            // iguais.

            // 4.3.12. Não igual: !=
            // 4.3.12.1. O tipo do operador é booleano.
            // 4.3.12.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.12.3. O valor da operação é verdadeiro sse os valores dos operandos não
            // forem iguais.

            // 4.3.13. Menor que: <
            // 4.3.13.1. O tipo do operador é booleano.
            // 4.3.13.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.13.3. Caso os operandos sejam numéricos:
            // 4.3.13.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // menor do que o da direita.
            // 4.3.13.4. Caso os operandos sejam strings:
            // 4.3.13.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética). A ordem
            // lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.13.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.14. Menor ou igual: <=
            // 4.3.14.1. O tipo do operador é booleano.
            // 4.3.14.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.14.3. Caso os operandos sejam numéricos:
            // 4.3.14.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // menor ou igual do que o da direita.
            // 4.3.14.4. Caso os operandos sejam strings:
            // 4.3.14.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética) ou for igual. A
            // ordem lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.14.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.15. Maior que: >
            // 4.3.15.1. O tipo do operador é booleano.
            // 4.3.15.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.15.3. Caso os operandos sejam numéricos:
            // 4.3.15.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // maior do que o da direita.
            // 4.3.15.4. Caso os operandos sejam strings:
            // 4.3.15.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética). A ordem
            // lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.15.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.16. Maior ou igual: >=
            // 4.3.16.1. O tipo do operador é booleano.
            // 4.3.16.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.16.3. Caso os operandos sejam numéricos:
            // 4.3.16.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // maior ou igual do que o da direita.
            // 4.3.16.4. Caso os operandos sejam strings:
            // 4.3.16.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // depois do que o da direita em ordem lexicográfica (alfabética) ou for igual.
            // A ordem lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.16.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.17. Soma: +
            // 4.3.17.1. O tipo da operação é o tipo dos operandos.
            // 4.3.17.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.17.3. Caso os operandos sejam inteiros:
            // 4.3.17.3.1. O valor da operação é a soma dos valores dos operandos.
            // 4.3.17.3.2. Caso a soma não caiba no tipo dos operandos, a operação é
            // inválida.
            // 4.3.17.4. Caso os operandos sejam floats:
            // 4.3.17.4.1. O valor da operação é a soma dos valores dos operandos.
            // 4.3.17.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.18. Subtração: -
            // 4.3.18.1. O tipo da operação é o tipo dos operandos.
            // 4.3.18.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.18.3. Caso os operandos sejam inteiros:
            // 4.3.18.3.1. O valor da operação é a subtração do lado esquerdo pelo lado
            // direito dos valores dos operandos.
            // 4.3.18.3.2. Caso a subtração não caiba no tipo dos operandos, a operação é
            // inválida.
            // 4.3.18.4. Caso os operandos sejam floats:
            // 4.3.18.4.1. O valor da operação é a subtração do lado esquerdo pelo lado
            // direito dos valores dos operandos.
            // 4.3.18.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.19. Multiplicação: *
            // 4.3.19.1. O tipo da operação é o tipo dos operandos.
            // 4.3.19.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.19.3. Caso os operandos sejam inteiros:
            // 4.3.19.3.1. O valor da operação é a multiplicação dos valores dos operandos.
            // 4.3.19.3.2. Caso a multiplicação não caiba no tipo dos operandos, a operação
            // é inválida.
            // 4.3.19.4. Caso os operandos sejam floats:
            // 4.3.19.4.1. O valor da operação é a multiplicação dos valores dos operandos.
            // 4.3.19.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.20. Divisão: /
            // 4.3.20.1. O tipo da operação é o tipo dos operandos.
            // 4.3.20.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.20.3. Caso os operandos sejam inteiros:
            // 4.3.20.3.1. O valor da operação é o quociente truncado dos valores dos
            // operandos.
            // 4.3.20.3.2. Caso o divisor seja zero, a operação é inválida.
            // 4.3.20.4. Caso os operandos sejam floats:
            // 4.3.20.4.1. O valor da operação é o quociente dos valores dos operandos.
            // 4.3.20.4.2. As regras de divisão por zero de floats do padrão IEEE-754 se
            // aplicam.
            // 4.3.20.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.21. Exponenciação: ^
            // 4.3.21.1. O tipo da operação é o tipo dos operandos.
            // 4.3.21.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.21.3. Caso os operandos sejam inteiros:
            // 4.3.21.3.1. O valor da operação é o valor da base elevado à potência do
            // expoente.
            // 4.3.21.3.2. Caso a exponenciação não caiba no tipo dos operandos, a operação
            // é inválida.
            // 4.3.21.3.3. Caso o expoente seja negativo, a operação é inválida.
            // 4.3.21.3.4. 0 ^ 0 é definido como 1.
            // 4.3.21.4. Caso os operandos sejam floats:
            // 4.3.21.4.1. O valor da operação é o valor da base elevado à potência do
            // expoente.
            // 4.3.21.4.2. 0 ^ 0 é definido como 1.
            // 4.3.21.4.3. As regras de exponenciação de floats do padrão IEEE-754 se
            // aplicam.

            // 4.3.22. Módulo: %
            // 4.3.22.1. O tipo da operação é o tipo dos operandos.
            // 4.3.22.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.22.3. Caso os operandos sejam inteiros:
            // 4.3.22.3.1. O valor da operação é o resto truncado da divisão euclidiana dos
            // valores dos operandos.
            // 4.3.22.3.2. Caso o divisor seja zero, a operação é inválida.
            // 4.3.22.4. Caso os operandos sejam floats:
            // 4.3.22.4.1. O valor da operação é o resto da divisão euclidiana dos valores
            // dos operandos.
            // 4.3.22.4.2. As regras de divisão por zero de floats do padrão IEEE-754 se
            // aplicam.
            // 4.3.22.5. Para qualquer outro tipo, a operação é inválida.
            case Expr.BinaryExpression(Expr left, Token operator, Expr right): {
                switch (operator.type()) {
                    case TokenType.EQUAL:
                    case TokenType.PERCENT_EQUAL:
                    case TokenType.PLUS_EQUAL:
                    case TokenType.MINUS_EQUAL:
                    case TokenType.SLASH_EQUAL:
                    case TokenType.STAR_EQUAL:
                    case TokenType.HAT_EQUAL:
                    case TokenType.DOT_DOT:
                    case TokenType.OR:
                    case TokenType.AND:
                    case TokenType.EQUAL_EQUAL:
                    case TokenType.BANG_EQUAL:
                    case TokenType.LESSER:
                    case TokenType.LESSER_EQUAL:
                    case TokenType.GREATER:
                    case TokenType.GREATER_EQUAL:
                    case TokenType.PLUS:
                    case TokenType.MINUS:
                    case TokenType.STAR:
                    case TokenType.SLASH:
                    case TokenType.HAT:
                    case TokenType.PERCENT:
                        throw new RuntimeException("Not implemented");
                    default:
                        throw new RuntimeException("Operação inválida: " + operator.type());
                }
            }
            case Expr.UnaryExpression(Token operator, Expr operand):
                throw new RuntimeException("Not implemented");
            case Expr.FunctionCall(Expr target, List<Expr.Argument> arguments):
                throw new RuntimeException("Not implemented");
            case Expr.Argument(Optional<Token> label, Expr value):
                throw new RuntimeException("Not implemented");
            case Expr.ListAccess(Expr target, Expr place):
                throw new RuntimeException("Not implemented");
            case Expr.RecAccess(Expr target, Token place):
                throw new RuntimeException("Not implemented");
            case Expr.ForExpression(Token variable, TypeAst type, Expr range, Expr.Block body):
                throw new RuntimeException("Not implemented");
            case Expr.IfExpression(Expr condition, Expr.Block thenBranch, Optional<Expr.Block> elseBranch):
                throw new RuntimeException("Not implemented");
            case Expr.WhileExpression(Expr condition, Expr body):
                throw new RuntimeException("Not implemented");
            case Expr.ReturnExpression(Expr value):
                throw new RuntimeException("Not implemented");
            case Expr.DebugExpression(Expr value):
                throw new RuntimeException("Not implemented");
            case Expr.Block(List<Stmt> statements, Optional<Stmt> lastStatement):
                throw new RuntimeException("Not implemented");
        }
    }

    private void error(Token token, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, token.where()));
    }

    private void error(FilePosition position, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }
}
