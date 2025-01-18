import java.sql.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class AplicacaoSimplificada {

    private static final String URL_DB = "caminho do banco de dados";
    private static final String USER = "nome do banco de dados";
    private static final String PASSWORD = "senha do banco de dados";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Escolha o número de sua opção:");
            System.out.println("1 – Buscar livro pelo título");
            System.out.println("2 – Listar livros registrados");
            System.out.println("3 – Listar autores registrados");
            System.out.println("4 – Listar autores vivos em um determinado ano");
            System.out.println("5 – Listar livros em um determinado idioma");
            System.out.println("0 – Sair");
            System.out.print("Escolha uma opção: ");
            int opcao = scanner.nextInt();
            scanner.nextLine(); // Consumir a quebra de linha

            switch (opcao) {
                case 1:
                    buscarLivroPeloTitulo(scanner);
                    break;
                case 2:
                    listarLivrosRegistrados();
                    break;
                case 3:
                    listarAutoresRegistrados();
                    break;
                case 4:
                    listarAutoresVivos(scanner);
                    break;
                case 5:
                    listarLivrosPorIdioma(scanner);
                    break;
                case 0:
                    System.out.println("Saindo...");
                    return;
                default:
                    System.out.println("Opção inválida.");
            }
        }
    }

    private static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL_DB, USER, PASSWORD);
    }

    private static void buscarLivroPeloTitulo(Scanner scanner) {
        System.out.print("Insira o nome do livro que vocẽ deseja procurar: ");
        String titulo = scanner.nextLine();

        // Buscar livro na Gutendex
        JSONObject livro = buscarLivroNaGutendex(titulo);

        if (livro == null) {
            System.out.println("Livro não encontrado.");
            return;
        }

        // Extrair detalhes do livro da resposta
        String idioma = livro.optString("language", "pt");
        int numeroDownloads = livro.optInt("downloads", 0);
        List<String> autores = new ArrayList<>();
        List<String> datasNascimento = new ArrayList<>();
        List<String> datasFalecimento = new ArrayList<>();
        JSONArray authorsArray = livro.optJSONArray("authors");

        if (authorsArray != null) {
            for (int i = 0; i < authorsArray.length(); i++) {
                JSONObject autorObj = authorsArray.getJSONObject(i);
                String nomeAutor = autorObj.optString("name", "Desconhecido");
                String nascimento = autorObj.optString("birth_year", "Desconhecido");
                String falecimento = autorObj.optString("death_year", "Ainda vivo");

                autores.add(nomeAutor);
                datasNascimento.add(nascimento);
                datasFalecimento.add(falecimento);
            }
        }

        // Exibir informações detalhadas do livro encontrado
        System.out.println("-------- Livro --------");
        System.out.println("Título: " + titulo);
        for (int i = 0; i < autores.size(); i++) {
            System.out.println("Autor(es): " + autores.get(i));
        }
        /*System.out.println("Autor(es):");
        for (int i = 0; i < autores.size(); i++) {
            System.out.println("- " + autores.get(i) + " (Nascimento: " + datasNascimento.get(i) + ", Falecimento: " + datasFalecimento.get(i) + ")");
        }*/
        System.out.println("Idioma: " + idioma);
        System.out.println("Número de downloads: " + numeroDownloads);
        System.out.println("-------------------------");

        // Salvando no banco de dados
        try (Connection conn = conectar()) {
            conn.setAutoCommit(false);

            // Inserir o livro no banco
            String sqlLivro = "INSERT INTO livros (titulo, idioma, numero_downloads) VALUES (?, ?, ?) ON CONFLICT (titulo) DO NOTHING RETURNING id";
            int livroId = -1;
            try (PreparedStatement stmtLivro = conn.prepareStatement(sqlLivro)) {
                stmtLivro.setString(1, titulo);
                stmtLivro.setString(2, idioma);
                stmtLivro.setInt(3, numeroDownloads);
                ResultSet rs = stmtLivro.executeQuery();
                if (rs.next()) {
                    livroId = rs.getInt(1);
                }
            }

            if (livroId == -1) {
                //System.out.println("Livro já existe no banco de dados.");
                conn.rollback();
                return;
            }

            // Inserir autores associados ao livro
            String sqlAutor = "INSERT INTO autores (nome, data_nascimento, data_falecimento) VALUES (?, ?, ?) ON CONFLICT (nome) DO NOTHING RETURNING id";
            String sqlLivroAutor = "INSERT INTO livros_autores (livro_id, autor_id) VALUES (?, ?)";
            for (int i = 0; i < autores.size(); i++) {
                String autor = autores.get(i);
                String nascimento = datasNascimento.get(i).equals("Desconhecido") ? null : datasNascimento.get(i);
                String falecimento = datasFalecimento.get(i).equals("Ainda vivo") ? null : datasFalecimento.get(i);

                int autorId;
                try (PreparedStatement stmtAutor = conn.prepareStatement(sqlAutor)) {
                    stmtAutor.setString(1, autor);
                    stmtAutor.setDate(2, nascimento != null ? java.sql.Date.valueOf(nascimento + "-01-01") : null);
                    stmtAutor.setDate(3, falecimento != null ? java.sql.Date.valueOf(falecimento + "-01-01") : null);
                    ResultSet rs = stmtAutor.executeQuery();
                    if (rs.next()) {
                        autorId = rs.getInt(1);
                    } else {
                        String sqlBuscaAutor = "SELECT id FROM autores WHERE nome = ?";
                        try (PreparedStatement stmtBuscaAutor = conn.prepareStatement(sqlBuscaAutor)) {
                            stmtBuscaAutor.setString(1, autor);
                            ResultSet rsBusca = stmtBuscaAutor.executeQuery();
                            rsBusca.next();
                            autorId = rsBusca.getInt(1);
                        }
                    }
                }

                try (PreparedStatement stmtLivroAutor = conn.prepareStatement(sqlLivroAutor)) {
                    stmtLivroAutor.setInt(1, livroId);
                    stmtLivroAutor.setInt(2, autorId);
                    stmtLivroAutor.executeUpdate();
                }
            }

            conn.commit();
            //System.out.println("Livro e autores salvos no banco de dados.");
        } catch (SQLException e) {
            System.out.println("Erro ao salvar o livro: " + e.getMessage());
        }
    }

    private static JSONObject buscarLivroNaGutendex(String titulo) {
        try {
            String tituloCodificado = URLEncoder.encode(titulo, StandardCharsets.UTF_8.toString());
            URL url = new URL("https://gutendex.com/books/?search=" + tituloCodificado);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                JSONObject jsonResponse = new JSONObject(response.toString());
                if (jsonResponse.getJSONArray("results").length() > 0) {
                    return jsonResponse.getJSONArray("results").getJSONObject(0);
                } else {
                    System.out.println("Nenhum livro encontrado.");
                    return null;
                }
            } else {
                System.out.println("Erro na resposta da API. Código de status: " + conn.getResponseCode());
                return null;
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar livro na Gutendex: " + e.getMessage());
            return null;
        }
    }

    private static void listarLivrosRegistrados() {
        try (Connection conn = conectar()) {
            String sql = "SELECT l.id, l.titulo, l.idioma, l.numero_downloads, array_agg(a.nome) AS autores FROM livros l " +
                    "LEFT JOIN livros_autores la ON l.id = la.livro_id " +
                    "LEFT JOIN autores a ON la.autor_id = a.id " +
                    "GROUP BY l.id";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    //System.out.println("ID: " + rs.getInt("id"));
                    System.out.println("Título: " + rs.getString("titulo"));
                    System.out.println("Idioma: " + rs.getString("idioma"));
                    System.out.println("Número de downloads: " + rs.getInt("numero_downloads"));
                    System.out.println("Autores: " + rs.getString("autores"));
                    System.out.println("-----------------------");
                }
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar livros: " + e.getMessage());
        }
    }

    private static void listarAutoresRegistrados() {
        try (Connection conn = conectar()) {
            String sql = "SELECT id, nome, data_nascimento, data_falecimento FROM autores ORDER BY nome";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);

                System.out.println("------ Autores Registrados ------");
                boolean encontrouAutores = false;
                while (rs.next()) {
                    encontrouAutores = true;
                    System.out.println("ID: " + rs.getInt("id"));
                    System.out.println("Nome: " + rs.getString("nome"));
                    System.out.println("Data de nascimento: " + rs.getDate("data_nascimento"));

                    java.sql.Date dataFalecimento = rs.getDate("data_falecimento");
                    if (dataFalecimento != null) {
                        System.out.println("Data de falecimento: " + dataFalecimento);
                    } else {
                        System.out.println("Data de falecimento: Ainda vivo");
                    }
                    System.out.println("-------------------------------");
                }

                if (!encontrouAutores) {
                    System.out.println("Nenhum autor encontrado no banco de dados.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar autores: " + e.getMessage());
        }
    }

    private static void listarAutoresVivos(Scanner scanner) {
        System.out.print("Digite o ano para verificar autores vivos: ");
        int ano = scanner.nextInt();

        try (Connection conn = conectar()) {
            String sql = "SELECT * FROM autores WHERE (data_falecimento IS NULL OR EXTRACT(YEAR FROM data_falecimento) > ?) " +
                    "AND EXTRACT(YEAR FROM data_nascimento) <= ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, ano);
                stmt.setInt(2, ano);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    System.out.println("ID: " + rs.getInt("id"));
                    System.out.println("Nome: " + rs.getString("nome"));
                    System.out.println("Data de nascimento: " + rs.getDate("data_nascimento"));
                    System.out.println("-----------------------");
                }
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar autores vivos: " + e.getMessage());
        }
    }

    private static void listarLivrosPorIdioma(Scanner scanner) {
        System.out.print("Digite o idioma: ");
        String idioma = scanner.nextLine();

        try (Connection conn = conectar()) {
            String sql = "SELECT * FROM livros WHERE idioma = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, idioma);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    System.out.println("ID: " + rs.getInt("id"));
                    System.out.println("Título: " + rs.getString("titulo"));
                    System.out.println("Idioma: " + rs.getString("idioma"));
                    System.out.println("Número de downloads: " + rs.getInt("numero_downloads"));
                    System.out.println("-----------------------");
                }
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar livros por idioma: " + e.getMessage());
        }
    }
}
