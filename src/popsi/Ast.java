package popsi;

import java.util.List;

// Nó base para a AST
public sealed interface Ast permits Program, Function, Parameter, Statement, Expression, Block {
}

// Programa -> Lista de Funções
public record Program(List<Function> functions) implements Ast {
}

// Função -> "fn" identificador (parametros)? -> tipo bloco
public record Function(
                String name,
                List<Parameter> parameters,
                String returnType,
                Block body) implements Ast {
}

// Parâmetro -> identificador : tipo
public record Parameter(String name, String type) implements Ast {
}

// Bloco -> "{" comando (";" comando)* ";"? "}"
public record Block(List<Statement> statements) implements Ast {
}

// Declarações de Comandos e Expressões
public sealed interface Statement extends Ast permits Declaration, ExpressionStatement {
}

// Declaração -> "let" identificador : tipo = expressão
public record Declaration(String name, String type, Expression value) implements Statement {
}

// Expressão como comando
public record ExpressionStatement(Expression expression) implements Statement {
}

// Declarações de Expressões
public sealed interface Expression extends Ast
                permits Literal, BinaryExpression, UnaryExpression, VariableExpression, FunctionCall {
}

// Literal -> número | string | caractere
public record Literal(Object value) implements Expression {
}

// Variável -> identificador
public record VariableExpression(String name) implements Expression {
}

// Operação Binária -> expressão operador expressão
public record BinaryExpression(
                Expression left,
                String operator,
                Expression right) implements Expression {
}

// Operação Unária -> operador expressão
public record UnaryExpression(String operator, Expression operand) implements Expression {
}

// Chamada de Função -> identificador ( argumentos? )
public record FunctionCall(
                String functionName,
                List<Expression> arguments) implements Expression {
}
