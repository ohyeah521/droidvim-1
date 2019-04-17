package jackpal.androidterm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.util.TermSettings;

import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MenuItem;

import com.droidvim.XmlUtils;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static jackpal.androidterm.Term.getPath;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class TermPreferences extends AppCompatPreferenceActivity {
    public static TermPreferences mTermPreference = null;

    public static final String FONT_FILENAME = "font_filename";
    public static final String FONT_PATH = Environment.getExternalStorageDirectory().getPath()+"/fonts";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private final static boolean FLAVOR_VIM = TermVimInstaller.FLAVOR_VIM;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppPickerList(this);
        setupTheme();
        setupActionBar();
        mTermPreference = this;

        super.onCreate(savedInstanceState);
    }

    private static String mLabels[] = null;
    private static String mPackageNames[] = null;
    public static void setAppPickerList(Activity activity) {
        try {
            final PackageManager pm = activity.getApplicationContext().getPackageManager();
            new Thread() {
                @Override
                public void run() {
                    final List<ApplicationInfo> installedAppList = pm.getInstalledApplications(0);
                    final TreeMap<String, String> items = new TreeMap<>();
                    for (ApplicationInfo app : installedAppList) {
                        Intent intent = pm.getLaunchIntentForPackage(app.packageName);
                        if (intent != null) items.put(app.loadLabel(pm).toString(), app.packageName);
                    }
                    List<String> list = new ArrayList<>(items.keySet());
                    mLabels = list.toArray(new String[0]);
                    list = new ArrayList<>(items.values());
                    mPackageNames = list.toArray(new String[0]);
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTheme() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final TermSettings settings = new TermSettings(getResources(), prefs);
        if (settings.getColorTheme() == 0) {
            setTheme(R.style.Theme_AppCompat);
        } else {
            setTheme(R.style.Theme_AppCompat_Light);
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (FLAVOR_VIM && AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
            loadHeadersFromResource(R.xml.pref_headers_vim, target);
        } else {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || ImePreferenceFragment.class.getName().equals(fragmentName)
                || FunctionbarPreferenceFragment.class.getName().equals(fragmentName)
                || GesturePreferenceFragment.class.getName().equals(fragmentName)
                || ScreenPreferenceFragment.class.getName().equals(fragmentName)
                || FontPreferenceFragment.class.getName().equals(fragmentName)
                || KeyboardPreferenceFragment.class.getName().equals(fragmentName)
                || ShellPreferenceFragment.class.getName().equals(fragmentName)
                || AppsPreferenceFragment.class.getName().equals(fragmentName)
                || PrefsPreferenceFragment.class.getName().equals(fragmentName);
    }

    private void documentTreePicker(int requestCode) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, requestCode);
        }
    }

    private void directoryPicker(String mes) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        final Activity activity = this;

        directoryPicker(mes, new ChooserDialog.Result() {
            @Override
            public void onChoosePath(String path, File pathFile) {
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                if (path == null) {
                    path = TermService.getAPPFILES() + "/home";
                    pathFile = new File(path);
                    if (!pathFile.exists()) pathFile.mkdir();
                    editor.putString("home_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_home_directory)+" "+path);
                } else if (new File(path).canWrite()) {
                    editor.putString("home_path", path);
                    editor.apply();
                    bld.setIcon(android.R.drawable.ic_dialog_info);
                    bld.setMessage(activity.getString(R.string.set_home_directory)+" "+path);
                } else {
                    bld.setIcon(android.R.drawable.stat_notify_error);
                    bld.setMessage(activity.getString(R.string.invalid_directory));
                }
                bld.setPositiveButton(activity.getString(android.R.string.ok), null);
                bld.create().show();
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void directoryPicker(String mes, final ChooserDialog.Result r) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(mes);
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                internalStoragePicker(r);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        bld.setNeutralButton(this.getString(R.string.reset_directory), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                r.onChoosePath(null, null);
            }
        });
        bld.create().show();
    }

    private void internalStoragePicker(ChooserDialog.Result r) {
        new ChooserDialog().with(this)
                .withResources(R.string.select_directory_message, R.string.select_directory, android.R.string.cancel)
                .enableOptions(true)
                .withFilter(true, true)
                .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                .withChosenListener(r)
                .build()
                .show();
    }

    void applicationInfo() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    void licensePrefs() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.license_text));
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        bld.setNeutralButton(this.getString(R.string.github), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent openUrl = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.github_url)));
                startActivity(openUrl);
            }
        });
        bld.create().show();
    }

    @SuppressLint("NewApi")
    void prefsPicker() {
        if (AndroidCompat.SDK >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PREFS_PICKER);
        } else {
            doPrefsPicker();
        }
    }

    @SuppressLint("NewApi")
    private void doPrefsPicker() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.prefs_dialog_rw));
        bld.setNeutralButton(this.getString(R.string.prefs_write), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                confirmWritePrefs();
            }
        });
        bld.setPositiveButton(this.getString(R.string.prefs_read), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/xml");
                startActivityForResult(intent, REQUEST_PREFS_READ_PICKER);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        bld.create().show();
    }

    private void confirmWritePrefs() {
        File pathExternalPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadDir = pathExternalPublicDir.getPath();
        @SuppressLint("SimpleDateFormat") String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        final String filename = downloadDir + "/"+ BuildConfig.APPLICATION_ID + "-" + timestamp + ".xml";
        writePrefs(filename);
    }

    private boolean writePrefs(String filename) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setPositiveButton(this.getString(android.R.string.ok), null);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(filename);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            XmlUtils.writeMapXml(pref.getAll(), fos);
        } catch (Exception e) {
            bld.setMessage(this.getString(R.string.prefs_write_info_failure));
            bld.create().show();
            return false;
        }
        bld.setMessage(this.getString(R.string.prefs_write_info_success)+"\n\n"+filename);
        bld.create().show();
        return true;
    }

    private boolean readPrefs(Uri uri, boolean clearPrefs) {
        try {
            InputStream is = this.getApplicationContext().getContentResolver().openInputStream(uri);
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Map<String, ?> entries = XmlUtils.readMapXml(is);

            int error = 0;
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                if (!prefs.contains(entry.getKey())) error += 1;
            }
            if (clearPrefs && error == 0) prefEdit.clear();

            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                putObject(prefEdit, entry.getKey(), entry.getValue());
            }
            prefEdit.apply();
        } catch (Exception e) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setIcon(android.R.drawable.ic_dialog_alert);
            bld.setTitle(this.getString(R.string.prefs_read_error_title));
            bld.setMessage(this.getString(R.string.prefs_read_error));
            bld.setPositiveButton(this.getString(android.R.string.ok), null);
            bld.create().show();
            return false;
        }
        return true;
    }

    private SharedPreferences.Editor putObject(final SharedPreferences.Editor edit, final String key, final Object val) {
        if (val instanceof Boolean)
            return edit.putBoolean(key, (Boolean) val);
        else if (val instanceof Float)
            return edit.putFloat(key, (Float) val);
        else if (val instanceof Integer)
            return edit.putInt(key, (Integer) val);
        else if (val instanceof Long)
            return edit.putLong(key, (Long) val);
        else if (val instanceof String) {
            String loadVal = (String)val;
            if (key.equals("home_path")) {
                if (!new File(loadVal).canWrite()) {
                    String defValue;
                    if (!BuildConfig.FLAVOR.equals("master")) {
                        defValue = TermService.getAPPFILES() + "/home";
                        File home = new File(defValue);
                        if (!home.exists()) home.mkdir();
                    } else {
                        defValue = getDir("HOME", MODE_PRIVATE).getAbsolutePath();
                    }
                    loadVal = defValue;
                }
                return edit.putString(key, loadVal);
            }
            return edit.putString(key, ((String)val));
        }
        return edit;
    }

    @SuppressLint("NewApi")
    private void filePicker() {
        if (AndroidCompat.SDK >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_FONT_PICKER);
        } else {
            doFilePicker();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void doFilePicker() {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setIcon(android.R.drawable.ic_dialog_info);
        bld.setMessage(this.getString(R.string.font_file_error));
        bld.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                startActivityForResult(intent, REQUEST_FONT_PICKER);
            }
        });
        bld.setNegativeButton(this.getString(android.R.string.no), null);
        final Activity activity = this;
        bld.setNeutralButton(this.getString(R.string.entry_fontfile_default), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                sp.edit().putString(FONT_FILENAME, activity.getString(R.string.entry_fontfile_default)).apply();
            }
        });
        bld.create().show();
    }

    public static final boolean DOCUMENT_TREE_PICKER_VER = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    public static final boolean USE_DOCUMENT_TREE_PICKER = false && DOCUMENT_TREE_PICKER_VER;
    public static final int REQUEST_FONT_PICKER          = 16;
    public static final int REQUEST_PREFS_READ_PICKER    = REQUEST_FONT_PICKER + 1;
    public static final int REQUEST_STORAGE_FONT_PICKER  = REQUEST_FONT_PICKER + 2;
    public static final int REQUEST_STORAGE_PREFS_PICKER = REQUEST_FONT_PICKER + 3;
    public static final int REQUEST_HOME_DIRECTORY       = REQUEST_FONT_PICKER + 4;
    public static final int REQUEST_STARTUP_DIRECTORY    = REQUEST_FONT_PICKER + 5;
    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE_FONT_PICKER:
            case REQUEST_STORAGE_PREFS_PICKER:
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            switch (requestCode) {
                                case REQUEST_STORAGE_FONT_PICKER:
                                    doFilePicker();
                                    break;
                                case REQUEST_STORAGE_PREFS_PICKER:
                                    doPrefsPicker();
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            AlertDialog.Builder bld = new AlertDialog.Builder(this);
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(this.getString(R.string.storage_permission_error));
                            bld.setPositiveButton(this.getString(android.R.string.ok), null);
                            bld.create().show();
                        }
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case REQUEST_STARTUP_DIRECTORY:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    String path = getDirectory(uri);
                    if (path != null && new File(path).canWrite()) {
                        ClipboardManagerCompat clip = ClipboardManagerCompatFactory.getManager(getApplicationContext());
                        clip.setText(path);
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.ic_dialog_info);
                        bld.setTitle(this.getString(R.string.title_startup_chooser_preference));
                        bld.setMessage(this.getString(R.string.copy_startup_dir)+" "+path);
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                    } else {
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.stat_notify_error);
                        bld.setMessage(this.getString(R.string.invalid_directory));
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                    }
                }
                break;
            case REQUEST_HOME_DIRECTORY:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    String path;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        path = getDirectory(uri);
                        File pathFile;
                        final Activity activity = this;
                        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        final SharedPreferences.Editor editor = prefs.edit();
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        if (path == null) {
                            path = TermService.getAPPFILES() + "/home";
                            pathFile = new File(path);
                            if (!pathFile.exists()) pathFile.mkdir();
                            editor.putString("home_path", path);
                            editor.apply();
                            bld.setIcon(android.R.drawable.ic_dialog_info);
                            bld.setMessage(activity.getString(R.string.set_home_directory)+" "+path);
                        } else if (new File(path).canWrite()) {
                            editor.putString("home_path", path);
                            editor.apply();
                            bld.setIcon(android.R.drawable.ic_dialog_info);
                            bld.setMessage(activity.getString(R.string.set_home_directory)+" "+path);
                        } else {
                            bld.setIcon(android.R.drawable.stat_notify_error);
                            bld.setMessage(activity.getString(R.string.invalid_directory));
                        }
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                        break;
                    }
                }
                break;
            case REQUEST_PREFS_READ_PICKER:
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (readPrefs(uri, false)) onCreate(null);
                    break;
                }
                break;
            case REQUEST_FONT_PICKER:
                String path;
                if (result == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    path = getPath(this, uri);
                    if (path != null && path.matches(".*\\.(?i)(ttf|ttc|otf)")) {
                        try {
                            File file = new File(path);
                            new Paint().setTypeface(Typeface.createFromFile(file));
                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                            sp.edit().putString(FONT_FILENAME, path).apply();
                        } catch (Exception e) {
                            AlertDialog.Builder bld = new AlertDialog.Builder(this);
                            bld.setIcon(android.R.drawable.ic_dialog_alert);
                            bld.setMessage(this.getString(R.string.font_file_invalid)+"\n\n"+path);
                            bld.setPositiveButton(this.getString(android.R.string.ok), null);
                            bld.create().show();
                        }
                    } else {
                        AlertDialog.Builder bld = new AlertDialog.Builder(this);
                        bld.setIcon(android.R.drawable.ic_dialog_alert);
                        bld.setMessage(this.getString(R.string.font_file_error));
                        bld.setPositiveButton(this.getString(android.R.string.ok), null);
                        bld.create().show();
                        break;
                    }
                }
                break;
            default:
                break;
        }
    }

    public static String getDirectory(Uri uri) {
        if (uri == null) return null;
        String path = null;
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            path = uri.getPath();
        } else if("content".equals(scheme)) {
            try {
                if ("com.android.externalstorage.documents".equals( uri.getAuthority())) { // ExternalStorageProvider
                    path = uri.getEncodedPath();
                    path = URLDecoder.decode(path, "UTF-8");
                    final String[] split = path.split(":");
                    final String type = split[0];
                    if ("/tree/primary".equalsIgnoreCase(type)) {
                        if (split.length == 1) return Environment.getExternalStorageDirectory().getAbsolutePath();
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    } else {
                        return "/stroage/" + type +  "/" + split[1];
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return path;
    }

    private ListPreference setFontList(ListPreference fontFileList) {
        File files[] = new File(FONT_PATH).listFiles();
        ArrayList<File> fonts = new ArrayList<File>();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().matches(".*\\.(?i)(ttf|ttc|otf)") && !file.isHidden()) {
                    fonts.add(file);
                }
            }
        }
        Collections.sort(fonts);
        int i = fonts.size() + 1;
        CharSequence[] items = new CharSequence[i];
        CharSequence[] values = new CharSequence[i];

        i = 0;
        Resources res = getResources();
        String systemFontName = res.getString(R.string.entry_fontfile_default);
        items[i] = systemFontName;
        values[i] = systemFontName;
        i++;

        for (File file : fonts) {
            items[i] = file.getName();
            values[i] = file.getName();
            i++;
        }

        fontFileList.setEntries(items);
        fontFileList.setEntryValues(values);
        return fontFileList;
    }

    public static class ImePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_ime);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("ime"));
            bindPreferenceSummaryToValue(findPreference("ime_direct_input_method"));
            bindPreferenceSummaryToValue(findPreference("ime_shortcuts_action_rev2"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class FunctionbarPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_functionbar);
            } else {
                addPreferencesFromResource(R.xml.pref_functionbar_20);
            }
            if (FLAVOR_VIM) {
                findPreference("functionbar_vim_paste").setDefaultValue(true);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("functionbar_diamond_action_rev2"));
            bindPreferenceSummaryToValue(findPreference("actionbar_invert_action"));
            bindPreferenceSummaryToValue(findPreference("actionbar_user_action"));
            bindPreferenceSummaryToValue(findPreference("actionbar_x_action"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class GesturePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_gesture);
            } else {
                addPreferencesFromResource(R.xml.pref_gesture_20);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("right_double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("left_double_tap_action"));
            bindPreferenceSummaryToValue(findPreference("bottom_double_tap_action"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class ScreenPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_screen);
            } else {
                addPreferencesFromResource(R.xml.pref_screen_20);
            }
            final String APP_INFO_KEY = "notification";
            Preference appInfoPref = getPreferenceScreen().findPreference(APP_INFO_KEY);
            appInfoPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.applicationInfo();
                    return true;
                }
            });
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("orientation"));
            bindPreferenceSummaryToValue(findPreference("cursorstyle"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class FontPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_font);
                final String FONT_FILE_PICKER_KEY = "fontfile_picker";
                Preference fontPrefs = getPreferenceScreen().findPreference(FONT_FILE_PICKER_KEY);
                fontPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mTermPreference != null) mTermPreference.filePicker();
                        return true;
                    }
                });
            } else {
                addPreferencesFromResource(R.xml.pref_font_18);
                final String FONTFILE = "fontfile";
                ListPreference fontFileList= (ListPreference) findPreference(FONTFILE);
                if (mTermPreference != null) mTermPreference.setFontList(fontFileList);

                Preference fontSelect = findPreference(FONTFILE);
                Resources res = getResources();
                fontSelect.setSummary(res.getString(R.string.summary_fontfile_preference)+String.format(" (%s)", FONT_PATH));
                fontSelect.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        ListPreference fontFileList = (ListPreference) preference;
                        if (mTermPreference != null) mTermPreference.setFontList(fontFileList);
                        return true;
                    }
                });
                fontSelect.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        ListPreference fontFileList = (ListPreference) preference;
                        if (mTermPreference != null) {
                            mTermPreference.setFontList(fontFileList);
                            fontFileList.setDefaultValue(newValue);
                        }
                        return true;
                    }
                });
            }
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class KeyboardPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_keyboard);
            } else {
                addPreferencesFromResource(R.xml.pref_keyboard_20);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("backaction"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class ShellPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                if (AndroidCompat.SDK > Build.VERSION_CODES.KITKAT) {
                    addPreferencesFromResource(R.xml.pref_shell);
                } else {
                    addPreferencesFromResource(R.xml.pref_shell_20);
                }
                final String HOME_KEY = "home_dir_chooser";
                Preference prefsHome = getPreferenceScreen().findPreference(HOME_KEY);
                prefsHome.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mTermPreference != null) {
                            if (USE_DOCUMENT_TREE_PICKER) {
                                mTermPreference.documentTreePicker(REQUEST_HOME_DIRECTORY);
                                return true;
                            }
                            mTermPreference.directoryPicker(getActivity().getString(R.string.choose_home_directory_message));
                        }
                        return true;
                    }
                });
                final String STARTUP_KEY = "startup_dir_chooser";
                Preference prefsStartup = getPreferenceScreen().findPreference(STARTUP_KEY);
                Activity activity = getActivity();
                final ChooserDialog.Result r = new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String path, File pathFile) {
                        if (path != null && new File(path).canWrite()) {
                            ClipboardManagerCompat clip = ClipboardManagerCompatFactory.getManager(activity.getApplicationContext());
                            clip.setText(path);
                            AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                            bld.setIcon(android.R.drawable.ic_dialog_info);
                            bld.setTitle(activity.getString(R.string.title_startup_chooser_preference));
                            bld.setMessage(activity.getString(R.string.copy_startup_dir) + " " + path);
                            bld.setPositiveButton(activity.getString(android.R.string.ok), null);
                            bld.create().show();
                        } else {
                            AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                            bld.setIcon(android.R.drawable.stat_notify_error);
                            bld.setMessage(activity.getString(R.string.invalid_directory));
                            bld.setPositiveButton(activity.getString(android.R.string.ok), null);
                            bld.create().show();
                        }
                    }
                };
                prefsStartup.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (USE_DOCUMENT_TREE_PICKER && mTermPreference != null) {
                            mTermPreference.documentTreePicker(REQUEST_STARTUP_DIRECTORY);
                            return true;
                        }
                        new ChooserDialog().with(activity)
                                .withResources(R.string.select_directory_message, R.string.select_directory, android.R.string.cancel)
                                .enableOptions(true)
                                .withFilter(true, true)
                                .withStartFile(Environment.getExternalStorageDirectory().getAbsolutePath())
                                .withChosenListener(r)
                                .build()
                                .show();
                        return true;
                    }
                });
            } else {
                addPreferencesFromResource(R.xml.pref_shell_18);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("termtype"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class AppsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_apps);

            String id = "external_app_package_name";
            ListPreference packageName = (ListPreference) getPreferenceScreen().findPreference(id);
            if (mTermPreference != null) {
                if (mLabels != null) packageName.setEntries(mLabels);
                if (mPackageNames != null) packageName.setEntryValues(mPackageNames);
            }
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("external_app_package_name"));
            bindPreferenceSummaryToValue(findPreference("external_app_button_mode"));
            bindPreferenceSummaryToValue(findPreference("cloud_dropbox_filepicker"));
            bindPreferenceSummaryToValue(findPreference("cloud_googledrive_filepicker"));
            bindPreferenceSummaryToValue(findPreference("cloud_onedrive_filepicker"));
            bindPreferenceSummaryToValue(findPreference("html_viewer_mode"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    public static class PrefsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (FLAVOR_VIM && AndroidCompat.SDK >= Build.VERSION_CODES.KITKAT) {
                addPreferencesFromResource(R.xml.pref_user_setting);
                final String PREFS_KEY = "prefs_rw";
                Preference prefsPicker = getPreferenceScreen().findPreference(PREFS_KEY);
                prefsPicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mTermPreference != null) mTermPreference.prefsPicker();
                        return true;
                    }
                });
            } else {
                addPreferencesFromResource(R.xml.pref_user_setting_18);
            }

            final String APP_INFO_KEY = "app_info";
            Preference appInfoPref = getPreferenceScreen().findPreference(APP_INFO_KEY);
            appInfoPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.applicationInfo();
                    return true;
                }
            });
            final String LICENSE_KEY = "license";
            Preference licensePrefs = getPreferenceScreen().findPreference(LICENSE_KEY);
            licensePrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (mTermPreference != null) mTermPreference.licensePrefs();
                    return true;
                }
            });
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), TermPreferences.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

}
