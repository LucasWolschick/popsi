package popsi;

public record FilePosition(int line, int column, String src) {
    public FilePosition nextLine() {
        return new FilePosition(line + 1, 1, src);
    }

    public FilePosition nextColumn() {
        return new FilePosition(line, column + 1, src);
    }

    public FilePosition previousColumn() {
        if (column == 1) {
            return this;
        } else {
            return new FilePosition(line, column - 1, src);
        }
    }
}
