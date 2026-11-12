package com.app.pocketticket;

/**
 * Archivo: app/src/main/java/com/app/pocketticket/Gasto.java
 *
 * Modelo POJO que representa un gasto guardado en SQLite.
 * Se usa un double para el monto porque la columna es REAL (MVP).
 */
public class Gasto {

    private long id;
    private String concepto;
    private double monto;
    private String fecha;
    private String imagenUri;

    /** Constructor vacío (útil para mapear desde un Cursor). */
    public Gasto() {
    }

    /** Constructor para INSERT (todavía sin id). */
    public Gasto(String concepto, double monto, String fecha, String imagenUri) {
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = fecha;
        this.imagenUri = imagenUri;
    }

    /** Constructor completo para SELECT. */
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

    /** true si el gasto tiene una foto de ticket asociada. */
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
