/*
 * ============================================================================
 *  PROYECTO: PocketTicket
 *  INTEGRANTES: [Espacio para nombres]
 *               ____________________________________________________________
 *               ____________________________________________________________
 *               ____________________________________________________________
 *  MATERIA / GRUPO: _______________________________________________________
 *  --------------------------------------------------------------------------
 *  DESCRIPCIÓN GENERAL
 *  MVP de finanzas personales en UNA sola pantalla:
 *    - Muestra el saldo disponible: $5,000.00 - suma de todos los gastos.
 *    - Permite agregar un gasto manual (concepto + monto).
 *    - Permite fotografiar un ticket con la cámara del dispositivo. La foto
 *      se guarda en el almacenamiento privado de la app y se entrega a la app
 *      de cámara con FileProvider (URI content:// permanente).
 *    - Cada gasto (con su foto, si existe) se guarda en SQLite y se muestra
 *      en un RecyclerView.
 *
 *  TECNOLOGÍAS: Java, SQLiteOpenHelper, RecyclerView, Material 3 y
 *               ActivityResultLauncher + FileProvider (cámara moderna).
 * ============================================================================
 */

package com.app.pocketticket;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Pantalla principal (y única) de PocketTicket.
 */
public class MainActivity extends AppCompatActivity {

    /* ------------------------------ Constantes ----------------------------- */

    /** Presupuesto inicial fijo del MVP. */
    private static final double PRESUPUESTO_INICIAL = 5000.0d;

    /** Máximo de filas que se consultan para la lista. */
    private static final int LIMITE_GASTOS_VISIBLES = 100;

    /** Debe coincidir con android:authorities="${applicationId}.fileprovider". */
    private static final String SUFIJO_PROVEEDOR = ".fileprovider";

    /** Subcarpeta donde se guardan las fotos (ver res/xml/file_paths.xml). */
    private static final String CARPETA_FOTOS = "tickets";

    private static final String KEY_FOTO_URI = "key_foto_uri";
    private static final String KEY_FOTO_RUTA = "key_foto_ruta";

    /* -------------------------------- Vistas ------------------------------- */

    private TextView tvSaldo;
    private TextView tvResumen;
    private TextView tvSinGastos;
    private RecyclerView rvGastos;

    /* --------------------------- Datos y adaptador ------------------------- */

    private DatabaseHelper db;
    private GastoAdapter adaptador;

    private final NumberFormat formatoMoneda =
            NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    /* ----------------------------- Cámara / foto --------------------------- */

    private ActivityResultLauncher<Uri> lanzadorCamara;
    private ActivityResultLauncher<String> lanzadorPermisoCamara;

    /** URI content:// que se está usando en la captura en curso. */
    @Nullable
    private Uri uriFotoPendiente;

    /** Archivo real detrás de la URI (para poder borrarlo si se cancela). */
    @Nullable
    private File archivoFotoPendiente;

    /* ------------------------------ Ciclo de vida -------------------------- */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = DatabaseHelper.getInstancia(this);

        enlazarVistas();
        configurarListaDeGastos();
        restaurarFotoPendiente(savedInstanceState);
        registrarLanzadores();
        configurarBotones();

        cargarDatos();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Si el usuario está en la app de cámara y el proceso muere, al volver
        // se recupera la URI para no perder la foto.
        if (uriFotoPendiente != null) {
            outState.putString(KEY_FOTO_URI, uriFotoPendiente.toString());
        }
        if (archivoFotoPendiente != null) {
            outState.putString(KEY_FOTO_RUTA, archivoFotoPendiente.getAbsolutePath());
        }
    }

    /* ------------------------------- Setup UI ------------------------------ */

    private void enlazarVistas() {
        tvSaldo = findViewById(R.id.tvSaldo);
        tvResumen = findViewById(R.id.tvResumen);
        tvSinGastos = findViewById(R.id.tvSinGastos);
        rvGastos = findViewById(R.id.rvGastos);
    }

    private void configurarListaDeGastos() {
        adaptador = new GastoAdapter(this);
        rvGastos.setLayoutManager(new LinearLayoutManager(this));
        rvGastos.setHasFixedSize(true);
        rvGastos.setAdapter(adaptador);
    }

    private void configurarBotones() {
        MaterialButton btnTomarFoto = findViewById(R.id.btnTomarFoto);
        MaterialButton btnGastoManual = findViewById(R.id.btnGastoManual);

        btnTomarFoto.setOnClickListener(v -> solicitarCamara());
        btnGastoManual.setOnClickListener(v -> mostrarDialogoGasto(null));
    }

    private void registrarLanzadores() {
        // Contrato oficial para ACTION_IMAGE_CAPTURE con EXTRA_OUTPUT.
        lanzadorCamara = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                guardada -> {
                    if (fotoValida()) {
                        mostrarDialogoGasto(uriFotoPendiente.toString());
                    } else {
                        borrarFotoPendiente();
                        mostrarToast(R.string.foto_no_guardada);
                    }
                });

        lanzadorPermisoCamara = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                concedido -> {
                    if (concedido) {
                        abrirCamara();
                    } else {
                        mostrarDialogoPermisoDenegado();
                    }
                });
    }

    /* -------------------------------- Saldo -------------------------------- */

    /**
     * Recarga la lista y recalcula el saldo desde SQLite.
     * saldo = 5000 - SUM(monto)
     */
    private void cargarDatos() {
        ArrayList<Gasto> gastos = db.obtenerUltimosGastos(LIMITE_GASTOS_VISIBLES);
        adaptador.actualizarLista(gastos);

        double totalGastado = db.obtenerTotalGastos();
        double saldo = PRESUPUESTO_INICIAL - totalGastado;

        tvSaldo.setText(formatoMoneda.format(saldo));
        tvSaldo.setTextColor(ContextCompat.getColor(this,
                saldo < 0 ? R.color.rojo_claro : R.color.blanco));

        int cantidad = db.contarGastos();
        tvResumen.setText(getString(R.string.saldo_resumen, cantidad,
                formatoMoneda.format(totalGastado)));

        boolean sinGastos = gastos.isEmpty();
        tvSinGastos.setVisibility(sinGastos ? View.VISIBLE : View.GONE);
        rvGastos.setVisibility(sinGastos ? View.GONE : View.VISIBLE);
    }

    /* ------------------------------- Cámara -------------------------------- */

    /** Punto de entrada del botón "Tomar foto ticket". */
    private void solicitarCamara() {
        if (!hayAppDeCamara()) {
            mostrarToast(R.string.sin_camara);
            return;
        }

        // Si la app declara android.permission.CAMERA, debe tenerla concedida
        // para poder lanzar ACTION_IMAGE_CAPTURE (SecurityException si no).
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamara();
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirCamara() {
        try {
            archivoFotoPendiente = crearArchivoDeFoto();
            if (archivoFotoPendiente == null) {
                mostrarToast(R.string.error_crear_foto);
                return;
            }

            uriFotoPendiente = FileProvider.getUriForFile(
                    this,
                    getPackageName() + SUFIJO_PROVEEDOR,
                    archivoFotoPendiente);

            lanzadorCamara.launch(uriFotoPendiente);

        } catch (IOException | IllegalArgumentException e) {
            borrarFotoPendiente();
            mostrarToast(R.string.error_crear_foto);
        }
    }

    /**
     * Crea el archivo destino de la foto.
     * Preferencia: getExternalFilesDir(Pictures)/tickets
     * Respaldo:    files/tickets
     * Ambas rutas están declaradas en res/xml/file_paths.xml.
     */
    @Nullable
    private File crearArchivoDeFoto() throws IOException {
        String marcaTiempo = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        String nombreArchivo = "TICKET_" + marcaTiempo + ".jpg";

        File carpeta = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (carpeta != null) {
            carpeta = new File(carpeta, CARPETA_FOTOS);
        } else {
            carpeta = new File(getFilesDir(), CARPETA_FOTOS);
        }

        if (!carpeta.exists() && !carpeta.mkdirs()) {
            return null;
        }
        return new File(carpeta, nombreArchivo);
    }

    /**
     * Algunos fabricantes devuelven resultCode != RESULT_OK aunque la foto sí
     * se guardó, así que se valida el archivo real en disco.
     */
    private boolean fotoValida() {
        return archivoFotoPendiente != null
                && archivoFotoPendiente.exists()
                && archivoFotoPendiente.length() > 0
                && uriFotoPendiente != null;
    }

    private boolean hayAppDeCamara() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        return intent.resolveActivity(getPackageManager()) != null;
    }

    private void borrarFotoPendiente() {
        if (archivoFotoPendiente != null && archivoFotoPendiente.exists()) {
            //noinspection ResultOfMethodCallIgnored
            archivoFotoPendiente.delete();
        }
        archivoFotoPendiente = null;
        uriFotoPendiente = null;
    }

    private void mostrarDialogoPermisoDenegado() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permiso_camara_titulo)
                .setMessage(R.string.permiso_camara_mensaje)
                .setPositiveButton(R.string.abrir_ajustes, (d, w) -> abrirAjustesDeLaApp())
                .setNegativeButton(R.string.cerrar, null)
                .show();
    }

    private void abrirAjustesDeLaApp() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    /* --------------------------- Diálogo de gasto -------------------------- */

    /**
     * Abre el AlertDialog para capturar concepto y monto.
     *
     * @param imagenUriTexto URI de la foto del ticket, o null si es gasto manual.
     */
    private void mostrarDialogoGasto(@Nullable final String imagenUriTexto) {

        final View vista = getLayoutInflater()
                .inflate(R.layout.dialog_agregar_gasto, null);

        final TextInputLayout tilConcepto = vista.findViewById(R.id.tilConcepto);
        final TextInputLayout tilMonto = vista.findViewById(R.id.tilMonto);
        final TextInputEditText etConcepto = vista.findViewById(R.id.etConcepto);
        final TextInputEditText etMonto = vista.findViewById(R.id.etMonto);
        final MaterialButton btnGuardar = vista.findViewById(R.id.btnGuardar);
        final MaterialButton btnCancelar = vista.findViewById(R.id.btnCancelar);
        final ImageView imgPreview = vista.findViewById(R.id.imgPreviewTicket);

        if (imagenUriTexto != null) {
            mostrarPreview(imgPreview, imagenUriTexto);
        }

        // Bandera para no borrar la foto cuando el gasto SÍ se guardó.
        final boolean[] guardado = new boolean[]{false};

        final AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(imagenUriTexto == null
                        ? R.string.dialog_titulo_manual
                        : R.string.dialog_titulo_ticket)
                .setView(vista)
                .setCancelable(true)
                .create();

        dialogo.setOnDismissListener(d -> {
            if (!guardado[0]) {
                // El usuario canceló: la foto temporal ya no se usa.
                borrarFotoPendiente();
            }
        });

        btnCancelar.setOnClickListener(v -> dialogo.dismiss());

        btnGuardar.setOnClickListener(v -> {
            tilConcepto.setError(null);
            tilMonto.setError(null);

            String concepto = textoDe(etConcepto);
            if (concepto.isEmpty()) {
                tilConcepto.setError(getString(R.string.error_concepto));
                etConcepto.requestFocus();
                return;
            }

            double monto = parsearMonto(textoDe(etMonto));
            if (!esMontoValido(monto)) {
                tilMonto.setError(getString(R.string.error_monto));
                etMonto.requestFocus();
                return;
            }

            long id = db.insertarGasto(concepto, monto, fechaActual(), imagenUriTexto);
            if (id == -1) {
                mostrarToast(R.string.error_guardar);
                return;
            }

            // La foto ahora pertenece al gasto: se desvincula del flujo temporal.
            guardado[0] = true;
            uriFotoPendiente = null;
            archivoFotoPendiente = null;

            dialogo.dismiss();
            cargarDatos();
            mostrarToast(R.string.gasto_guardado);
        });

        dialogo.show();

        // Mostrar el teclado con el primer campo enfocado.
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        etConcepto.requestFocus();
    }

    private void mostrarPreview(@NonNull ImageView destino, @NonNull String uriTexto) {
        try {
            Bitmap miniatura = ImagenUtils.cargarBitmap(this, Uri.parse(uriTexto), 800);
            if (miniatura != null) {
                destino.setImageBitmap(miniatura);
                destino.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            destino.setVisibility(View.GONE);
        }
    }

    /* ------------------------------ Utilidades ----------------------------- */

    @NonNull
    private static String textoDe(@NonNull EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    /**
     * Acepta "1250.50" y "1250,50" (coma decimal). Devuelve -1 si no es válido.
     */
    private static double parsearMonto(@Nullable String texto) {
        if (texto == null) {
            return -1d;
        }
        String limpio = texto.trim()
                .replace("$", "")
                .replace(" ", "")
                .replace(',', '.');

        if (limpio.isEmpty()) {
            return -1d;
        }
        try {
            return Double.parseDouble(limpio);
        } catch (NumberFormatException e) {
            return -1d;
        }
    }

    private static boolean esMontoValido(double monto) {
        return monto > 0d && !Double.isNaN(monto) && !Double.isInfinite(monto);
    }

    @NonNull
    private static String fechaActual() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
    }

    private void mostrarToast(@StringRes int mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    private void restaurarFotoPendiente(@Nullable Bundle estado) {
        if (estado == null) {
            return;
        }
        String uri = estado.getString(KEY_FOTO_URI);
        String ruta = estado.getString(KEY_FOTO_RUTA);

        if (uri != null) {
            uriFotoPendiente = Uri.parse(uri);
        }
        if (ruta != null) {
            archivoFotoPendiente = new File(ruta);
        }
    }
}
