package cn.local.jiyibi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 应用私有目录中的本地数据库。 */
public final class LedgerDatabase extends SQLiteOpenHelper {
    private static final String TABLE = "ledger_entry";
    private static final int DATABASE_VERSION = 1;

    public LedgerDatabase(Context context) {
        this(context, "ledger.db");
    }

    LedgerDatabase(Context context, String databaseName) {
        super(applicationContext(context), databaseName, null, DATABASE_VERSION);
    }

    private static Context applicationContext(Context context) {
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE ledger_entry (id TEXT PRIMARY KEY, date TEXT NOT NULL, "
                + "shop TEXT NOT NULL, product TEXT NOT NULL, category TEXT NOT NULL, "
                + "amount TEXT NOT NULL, note TEXT NOT NULL, source_text TEXT NOT NULL, "
                + "status TEXT NOT NULL CHECK(status IN ('draft','confirmed')))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("数据库迁移尚未定义，禁止自动删除账目");
    }

    public void save(LedgerEntry entry) {
        long result = getWritableDatabase().insertWithOnConflict(TABLE, null, values(entry), SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) {
            throw new android.database.SQLException("账目保存失败");
        }
    }

    /** 批量恢复采用事务并按稳定标识跳过已有记录，禁止覆盖用户后续编辑。 */
    public int merge(List<LedgerEntry> entries) {
        SQLiteDatabase database = getWritableDatabase();
        int count = 0;
        database.beginTransaction();
        try {
            for (LedgerEntry entry : entries) {
                if (database.insertWithOnConflict(TABLE, null, values(entry), SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                    count++;
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return count;
    }

    public List<LedgerEntry> list() {
        List<LedgerEntry> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
                "CASE WHEN status='draft' THEN 0 ELSE 1 END, date DESC, rowid DESC")) {
            while (cursor.moveToNext()) {
                LedgerEntry entry = new LedgerEntry();
                entry.setId(value(cursor, "id"));
                entry.setDate(value(cursor, "date"));
                entry.setShop(value(cursor, "shop"));
                entry.setProduct(value(cursor, "product"));
                entry.setCategory(value(cursor, "category"));
                entry.setAmount(value(cursor, "amount"));
                entry.setNote(value(cursor, "note"));
                entry.setSourceText(value(cursor, "source_text"));
                entry.setStatus(value(cursor, "status"));
                result.add(entry);
            }
        }
        return result;
    }

    public LedgerEntry find(String id) {
        for (LedgerEntry entry : list()) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public void delete(String id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{id});
    }

    private static String value(Cursor cursor, String name) {
        return cursor.getString(cursor.getColumnIndexOrThrow(name));
    }

    private static ContentValues values(LedgerEntry entry) {
        ContentValues values = new ContentValues();
        values.put("id", entry.getId());
        values.put("date", entry.getDate());
        values.put("shop", entry.getShop());
        values.put("product", entry.getProduct());
        values.put("category", entry.getCategory());
        values.put("amount", entry.getAmount());
        values.put("note", entry.getNote());
        values.put("source_text", entry.getSourceText());
        values.put("status", entry.getStatus());
        return values;
    }
}
