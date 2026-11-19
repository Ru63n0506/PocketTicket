package com.app.pocketticket;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NOMBRE = "pocketticket.db";
    public static final int DB_VERSION = 3;
    public static final double SALDO_INICIAL = 5000.0d;

    public static final String TABLA_USUARIOS = "usuarios";
    public static final String TABLA_GASTOS = "gastos";

    public static final String COL_ID = "id";
    public static final String COL_NOMBRE = "nombre";
    public static final String COL_CORREO = "correo";
    public static final String COL_SALDO = "saldo";
    public static final String COL_CREADO_EN = "creado_en";
    public static final String COL_CONCEPTO = "concepto";
    public static final String COL_MONTO = "monto";
    public static final String COL_FECHA = "fecha";
    public static final String COL_IMAGEN_URI = "imagen_uri";
    public static final String COL_USUARIO_ID = "usuario_id";

    private static final String TABLA_INGRESOS_ANTERIOR = "ingresos";
    private static final String TABLA_GASTOS_ANTERIOR = "gastos_anterior";

    private static final String CREAR_TABLA_USUARIOS =
            "CREATE TABLE IF NOT EXISTS " + TABLA_USUARIOS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NOMBRE + " TEXT NOT NULL, " +
                    COL_CORREO + " TEXT, " +
                    COL_SALDO + " REAL NOT NULL DEFAULT 5000, " +
                    COL_CREADO_EN + " TEXT);";

    private static final String CREAR_TABLA_GASTOS =
            "CREATE TABLE IF NOT EXISTS " + TABLA_GASTOS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CONCEPTO + " TEXT, " +
                    COL_MONTO + " REAL, " +
                    COL_FECHA + " TEXT, " +
                    COL_IMAGEN_URI + " TEXT, " +
                    COL_USUARIO_ID + " INTEGER NOT NULL DEFAULT 1, " +
                    "FOREIGN KEY (" + COL_USUARIO_ID + ") REFERENCES " +
                    TABLA_USUARIOS + "(" + COL_ID + ") ON DELETE CASCADE);";

    private static volatile DatabaseHelper instancia;

    private final Context contexto;

    public static DatabaseHelper getInstancia(Context context) {
        if (instancia == null) {
            synchronized (DatabaseHelper.class) {
                if (instancia == null) {
                    instancia = new DatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instancia;
    }

    public DatabaseHelper(Context context) {
        super(context, DB_NOMBRE, null, DB_VERSION);
        this.contexto = context.getApplicationContext();
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREAR_TABLA_USUARIOS);
        db.execSQL(CREAR_TABLA_GASTOS);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_usuario ON " +
                TABLA_GASTOS + "(" + COL_USUARIO_ID + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON " +
                TABLA_GASTOS + "(" + COL_FECHA + ");");
        crearUsuarioEn(db, contexto.getString(R.string.usuario_principal), "", SALDO_INICIAL);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            migrarAUsuarios(db);
        }
    }

    private void migrarAUsuarios(@NonNull SQLiteDatabase db) {
        db.execSQL(CREAR_TABLA_USUARIOS);

        double saldoInicial = SALDO_INICIAL;
        if (tablaExiste(db, TABLA_INGRESOS_ANTERIOR)) {
            saldoInicial += sumaDeTabla(db, TABLA_INGRESOS_ANTERIOR);
        }

        long usuarioId = crearUsuarioEn(db,
                contexto.getString(R.string.usuario_principal), "", saldoInicial);
        if (usuarioId == -1) {
            throw new IllegalStateException("No se pudo crear el usuario inicial");
        }

        db.execSQL("ALTER TABLE " + TABLA_GASTOS + " RENAME TO " + TABLA_GASTOS_ANTERIOR);
        db.execSQL(CREAR_TABLA_GASTOS);
        db.execSQL("INSERT INTO " + TABLA_GASTOS + " (" +
                COL_ID + ", " + COL_CONCEPTO + ", " + COL_MONTO + ", " +
                COL_FECHA + ", " + COL_IMAGEN_URI + ", " + COL_USUARIO_ID + ") " +
                "SELECT " + COL_ID + ", " + COL_CONCEPTO + ", " + COL_MONTO + ", " +
                COL_FECHA + ", " + COL_IMAGEN_URI + ", " + usuarioId +
                " FROM " + TABLA_GASTOS_ANTERIOR);
        db.execSQL("DROP TABLE " + TABLA_GASTOS_ANTERIOR);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_usuario ON " +
                TABLA_GASTOS + "(" + COL_USUARIO_ID + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON " +
                TABLA_GASTOS + "(" + COL_FECHA + ");");
        db.execSQL("DROP TABLE IF EXISTS " + TABLA_INGRESOS_ANTERIOR);
    }

    private boolean tablaExiste(@NonNull SQLiteDatabase db, @NonNull String tabla) {
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{tabla})) {
            return cursor.moveToFirst();
        }
    }

    private double sumaDeTabla(@NonNull SQLiteDatabase db, @NonNull String tabla) {
        try (Cursor cursor = db.rawQuery(
                "SELECT IFNULL(SUM(" + COL_MONTO + "), 0) FROM " + tabla, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }
        return 0.0d;
    }

    public long crearUsuario(@NonNull String nombre, @Nullable String correo) {
        return crearUsuario(nombre, correo, SALDO_INICIAL);
    }

    public long crearUsuario(@NonNull String nombre,
                             @Nullable String correo,
                             double saldoInicial) {
        return crearUsuarioEn(getWritableDatabase(), nombre, correo, saldoInicial);
    }

    private long crearUsuarioEn(@NonNull SQLiteDatabase db,
                                @NonNull String nombre,
                                @Nullable String correo,
                                double saldoInicial) {
        ContentValues valores = new ContentValues();
        valores.put(COL_NOMBRE, nombre);
        valores.put(COL_CORREO, correo == null ? "" : correo);
        valores.put(COL_SALDO, saldoInicial);
        valores.put(COL_CREADO_EN, String.valueOf(System.currentTimeMillis()));

        try {
            return db.insert(TABLA_USUARIOS, null, valores);
        } catch (Exception e) {
            return -1;
        }
    }

    @NonNull
    public ArrayList<Usuario> obtenerTodosLosUsuarios() {
        ArrayList<Usuario> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] columnas = {COL_ID, COL_NOMBRE, COL_CORREO, COL_SALDO};

        try (Cursor cursor = db.query(TABLA_USUARIOS, columnas, null, null,
                null, null, COL_ID + " ASC")) {
            while (cursor.moveToNext()) {
                lista.add(usuarioDesdeCursor(cursor));
            }
        }
        return lista;
    }

    @Nullable
    public Usuario obtenerUsuario(long id) {
        SQLiteDatabase db = getReadableDatabase();
        String[] columnas = {COL_ID, COL_NOMBRE, COL_CORREO, COL_SALDO};

        try (Cursor cursor = db.query(TABLA_USUARIOS, columnas,
                COL_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return usuarioDesdeCursor(cursor);
            }
        }
        return null;
    }

    public int contarUsuarios() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLA_USUARIOS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public boolean actualizarUsuario(long id,
                                     @NonNull String nombre,
                                     @Nullable String correo) {
        ContentValues valores = new ContentValues();
        valores.put(COL_NOMBRE, nombre);
        valores.put(COL_CORREO, correo == null ? "" : correo);

        try {
            int filas = getWritableDatabase().update(TABLA_USUARIOS, valores,
                    COL_ID + " = ?", new String[]{String.valueOf(id)});
            return filas > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean sumarSaldoUsuario(long id, double monto) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement sentencia = db.compileStatement(
                "UPDATE " + TABLA_USUARIOS + " SET " + COL_SALDO + " = " +
                        COL_SALDO + " + ? WHERE " + COL_ID + " = ?");
        try {
            sentencia.bindDouble(1, monto);
            sentencia.bindLong(2, id);
            return sentencia.executeUpdateDelete() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            sentencia.close();
        }
    }

    public boolean eliminarUsuario(long idSesion, long idObjetivo) {
        if (idSesion != idObjetivo) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLA_GASTOS, COL_USUARIO_ID + " = ?",
                    new String[]{String.valueOf(idObjetivo)});
            int filas = db.delete(TABLA_USUARIOS, COL_ID + " = ?",
                    new String[]{String.valueOf(idObjetivo)});
            db.setTransactionSuccessful();
            return filas > 0;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public long insertarGasto(long usuarioId,
                              @NonNull String concepto,
                              double monto,
                              @Nullable String fecha,
                              @Nullable String imagenUri) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues valores = new ContentValues();
        valores.put(COL_USUARIO_ID, usuarioId);
        valores.put(COL_CONCEPTO, concepto);
        valores.put(COL_MONTO, monto);
        valores.put(COL_FECHA, fecha);

        if (imagenUri == null || imagenUri.trim().isEmpty()) {
            valores.putNull(COL_IMAGEN_URI);
        } else {
            valores.put(COL_IMAGEN_URI, imagenUri);
        }

        try {
            return db.insert(TABLA_GASTOS, null, valores);
        } catch (Exception e) {
            return -1;
        }
    }

    public long insertarGasto(@NonNull Gasto gasto) {
        return insertarGasto(gasto.getUsuarioId(), gasto.getConcepto(),
                gasto.getMonto(), gasto.getFecha(), gasto.getImagenUri());
    }

    @NonNull
    public ArrayList<Gasto> obtenerGastosDeUsuario(long usuarioId) {
        return consultarGastos(usuarioId, null);
    }

    @NonNull
    public ArrayList<Gasto> obtenerUltimosGastosDeUsuario(long usuarioId, int limite) {
        if (limite <= 0) {
            return new ArrayList<>();
        }
        return consultarGastos(usuarioId, String.valueOf(limite));
    }

    public double obtenerTotalGastosDeUsuario(long usuarioId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT IFNULL(SUM(" + COL_MONTO + "), 0) FROM " +
                TABLA_GASTOS + " WHERE " + COL_USUARIO_ID + " = ?";

        try (Cursor cursor = db.rawQuery(sql,
                new String[]{String.valueOf(usuarioId)})) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }
        return 0.0d;
    }

    public int contarGastosDeUsuario(long usuarioId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + TABLA_GASTOS +
                " WHERE " + COL_USUARIO_ID + " = ?";

        try (Cursor cursor = db.rawQuery(sql,
                new String[]{String.valueOf(usuarioId)})) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public boolean eliminarGasto(long usuarioId, long gastoId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int filas = db.delete(TABLA_GASTOS,
                    COL_ID + " = ? AND " + COL_USUARIO_ID + " = ?",
                    new String[]{String.valueOf(gastoId), String.valueOf(usuarioId)});
            return filas > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private ArrayList<Gasto> consultarGastos(long usuarioId, @Nullable String limite) {
        ArrayList<Gasto> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String[] columnas = {
                COL_ID, COL_USUARIO_ID, COL_CONCEPTO, COL_MONTO,
                COL_FECHA, COL_IMAGEN_URI
        };

        try (Cursor cursor = db.query(
                TABLA_GASTOS,
                columnas,
                COL_USUARIO_ID + " = ?",
                new String[]{String.valueOf(usuarioId)},
                null,
                null,
                COL_ID + " DESC",
                limite)) {

            while (cursor.moveToNext()) {
                lista.add(gastoDesdeCursor(cursor));
            }
        }
        return lista;
    }

    @NonNull
    private Usuario usuarioDesdeCursor(@NonNull Cursor cursor) {
        Usuario usuario = new Usuario();
        usuario.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        usuario.setNombre(cursor.getString(cursor.getColumnIndexOrThrow(COL_NOMBRE)));
        usuario.setCorreo(cursor.getString(cursor.getColumnIndexOrThrow(COL_CORREO)));
        usuario.setSaldo(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SALDO)));
        return usuario;
    }

    @NonNull
    private Gasto gastoDesdeCursor(@NonNull Cursor cursor) {
        Gasto gasto = new Gasto();
        gasto.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        gasto.setUsuarioId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_USUARIO_ID)));
        gasto.setConcepto(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONCEPTO)));
        gasto.setMonto(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MONTO)));
        gasto.setFecha(cursor.getString(cursor.getColumnIndexOrThrow(COL_FECHA)));
        gasto.setImagenUri(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGEN_URI)));
        return gasto;
    }
}