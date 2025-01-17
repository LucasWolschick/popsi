package popsi;

public sealed interface Result<T, E> permits Result.Success, Result.Error {
    public T unwrap();

    public E unwrapErr();

    public boolean isSuccess();

    record Success<T, E>(T value) implements Result<T, E> {
        public T unwrap() {
            return value;
        }

        public E unwrapErr() {
            throw new IllegalStateException("Cannot unwrapErr a success");
        }

        public boolean isSuccess() {
            return true;
        }
    }

    record Error<T, E>(E error) implements Result<T, E> {
        public T unwrap() {
            throw new IllegalStateException("Cannot unwrap an error");
        }

        public E unwrapErr() {
            return error;
        }

        public boolean isSuccess() {
            return false;
        }
    }
}
