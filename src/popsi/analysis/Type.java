package popsi.analysis;

import java.util.List;
import java.util.Set;

public sealed interface Type {
    public static record Named(String name, List<Type> args) implements Type {
    }

    public static record Function(List<Type> args, Type ret) implements Type {
    }

    /// Tipo Record, usado para representar registros.
    public static record Record(List<String> fields, List<Type> types) implements Type {
    }

    /// Inteiro de comprimento desconhecido.
    /// Pode ser convertido para qualquer tipo inteiro.
    public static final Named I_LITERAL = new Named("{integer}", List.of());

    /// Inteiro sem sinal de 8 bits.
    public static final Named U8 = new Named("u8", List.of());

    /// Inteiro sem sinal de 16 bits.
    public static final Named U16 = new Named("u16", List.of());

    /// Inteiro sem sinal de 32 bits.
    public static final Named U32 = new Named("u32", List.of());

    /// Inteiro sem sinal de 64 bits.
    public static final Named U64 = new Named("u64", List.of());

    /// Inteiro com sinal de 8 bits.
    public static final Named I8 = new Named("i8", List.of());

    /// Inteiro com sinal de 16 bits.
    public static final Named I16 = new Named("i16", List.of());

    /// Inteiro com sinal de 32 bits.
    public static final Named I32 = new Named("i32", List.of());

    /// Inteiro com sinal de 64 bits.
    public static final Named I64 = new Named("i64", List.of());

    /// Número de ponto flutuante de comprimento desconhecido.
    /// Pode ser convertido para qualquer tipo de ponto flutuante.
    public static final Named F_LITERAL = new Named("{float}", List.of());

    /// Número de ponto flutuante de precisão simples.
    public static final Named F32 = new Named("f32", List.of());

    /// Número de ponto flutuante de precisão dupla.
    public static final Named F64 = new Named("f64", List.of());

    /// String.
    public static final Named STR = new Named("str", List.of());

    /// Caractere.
    public static final Named CHAR = new Named("char", List.of());

    /// Tipo desconhecido. Esse tipo não foi determinado ainda, mas ele existe.
    /// Pode ser convertido para qualquer tipo.
    public static final Named ANY = new Named("any", List.of());

    /// Tipo nulo. Não existem valores desse tipo.
    public static final Named NOTHING = new Named("nothing", List.of());

    /// Tipo inválido. Produzido quando ocorre um erro de tipo.
    public static final Named INVALID = new Named("?", List.of());

    public static final Named UNIT = new Named("unit", List.of());

    /// Tipo booleano, usado para condições lógicas.
    public static final Named BOOLEAN = new Named("bool", List.of());

    public static class TypeAlgebra {
        public static boolean isList(Type t) {
            return t instanceof Named named && named.name.equals("[]");
        }

        // Lowest upper bound (supremo): o tipo mais genérico que é supertipo de
        // ambos.
        public static Type lub(Type a, Type b) {
            if (a.equals(b)) {
                return a;
            } else if (a.equals(ANY)) {
                return b;
            } else if (b.equals(ANY)) {
                return a;
            } else if (a.equals(I_LITERAL)) {
                var numerical = Set.of(U8, U16, U32, U64, I8, I16, I32, I64);
                if (numerical.contains(b)) {
                    return b;
                } else {
                    return ANY;
                }
            } else if (b.equals(I_LITERAL)) {
                return lub(b, a);
            } else if (a.equals(F_LITERAL)) {
                var numerical = Set.of(F32, F64);
                if (numerical.contains(b)) {
                    return b;
                } else {
                    return ANY;
                }
            } else if (b.equals(F_LITERAL)) {
                return lub(b, a);
            } else if (isList(a) && isList(b)) {
                var listA = (Named) a;
                var listB = (Named) b;
                return new Named("[]", List.of(lub(listA.args.get(0), listB.args.get(0))));
            } else {
                return ANY;
            }
        }

        // greatest lower bound (ínfimo): o tipo mais específico que é subtipo de ambos.
        public static Type glb(Type a, Type b) {
            if (a.equals(b)) {
                return a;
            } else if (a.equals(ANY)) { // (i32, any) = i32
                return b;
            } else if (b.equals(ANY)) {
                return a;
            } else if (a.equals(I_LITERAL)) {
                var numerical = Set.of(U8, U16, U32, U64, I8, I16, I32, I64);
                if (numerical.contains(b)) {
                    return a;
                } else {
                    return NOTHING;
                }
            } else if (b.equals(I_LITERAL)) {
                return glb(b, a);
            } else if (a.equals(F_LITERAL)) {
                var numerical = Set.of(F32, F64);
                if (numerical.contains(b)) {
                    return a;
                } else {
                    return NOTHING;
                }
            } else if (b.equals(F_LITERAL)) {
                return glb(b, a);
            } else if (isList(a) && isList(b)) {
                var listA = (Named) a;
                var listB = (Named) b;
                return new Named("[]", List.of(glb(listA.args.get(0), listB.args.get(0))));
            } else {
                return NOTHING;
            }
        }
    }
}
