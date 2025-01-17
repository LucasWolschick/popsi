# Popsi

Uma linguagem de expressões estaticamente tipada, de sintaxe similar ao Rust, com coletor de lixo.

Atualmente contém o front-end desenvolvido, com análises léxica, sintática e semântica. O plano é implementar um gerador de código LLVM IR para compilação nativa.

Desenvolvida para a disciplina de Compiladores no Bacharelado de Ciência da Computação da Universidade Estadual de Maringá.

Autores:

- [Guilherme Frare Clemente](https://github.com/GuiSebax)
- [Lucas Wolschick](https://github.com/LucasWolschick)
- [Marcos Vinicius de Oliveira](https://github.com/marcosoliveira-hub)

## Executando

Este projeto requer que Java 23 e Gradle 8 estejam instalados em sua máquina.

Para compilar, abra um terminal na pasta raiz do código-fonte e execute:

```bash
$ ./gradlew clean build
```

O compilador será gerado no arquivo `./build/libs/popsi-1.0.jar`. Para compilar um arquivo Popsi (existem alguns exemplos em `test/`):

```bash
$ java -jar ./build/libs/popsi-1.0.jar test/recursivo.psi
```

Para mais exemplos de sintaxe, veja a pasta `test/` e as gramáticas na pasta `design/`.
