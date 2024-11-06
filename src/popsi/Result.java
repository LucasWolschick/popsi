package popsi;

public sealed interface Result<T, E> permits Result.Success, Result.Error {
    record Success<T, E>(T value) implements Result<T, E> {
    }

    record Error<T, E>(E error) implements Result<T, E> {
    }
}
