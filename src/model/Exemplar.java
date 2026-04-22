package model;

public class Exemplar {
    private int id;
    private int livroId;
    private String codigoPatrimonio;
    private String estado;

    public Exemplar() {}

    public Exemplar(int id, int livroId, String codigoPatrimonio, String estado) {
        this.id = id;
        this.livroId = livroId;
        this.codigoPatrimonio = codigoPatrimonio;
        this.estado = estado;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getLivroId() {
        return livroId;
    }

    public void setLivroId(int livroId) {
        this.livroId = livroId;
    }

    public String getCodigoPatrimonio() {
        return codigoPatrimonio;
    }

    public void setCodigoPatrimonio(String codigoPatrimonio) {
        this.codigoPatrimonio = codigoPatrimonio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "Exemplar{" +
                "id=" + id +
                ", livroId=" + livroId +
                ", codigoPatrimonio='" + codigoPatrimonio + '\'' +
                ", estado='" + estado + '\'' +
                '}';
    }
}
