package com.app.pocketticket;

public class Gasto {

    private long id;
    private String concepto;
    private double monto;
    private String fecha;
    private String imagenUri;

    public Gasto() {
    }

    public Gasto(String concepto, double monto, String fecha, String imagenUri) {
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = fecha;
        this.imagenUri = imagenUri;
    }

    public Gasto(long id, String concepto, double monto, String fecha, String imagenUri) {
        this.id = id;
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = fecha;
        this.imagenUri = imagenUri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getImagenUri() {
        return imagenUri;
    }

    public void setImagenUri(String imagenUri) {
        this.imagenUri = imagenUri;
    }

    public boolean tieneImagen() {
        return imagenUri != null && !imagenUri.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "Gasto{" +
                "id=" + id +
                ", concepto='" + concepto + '\'' +
                ", monto=" + monto +
                ", fecha='" + fecha + '\'' +
                ", imagenUri='" + imagenUri + '\'' +
                '}';
    }
}