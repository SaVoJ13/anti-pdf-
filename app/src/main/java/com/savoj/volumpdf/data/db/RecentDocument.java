package com.savoj.volumpdf.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "recent_documents")
public class RecentDocument {

    @PrimaryKey
    @NonNull
    private final String uri;
    private final String title;
    private final int pageCount;
    private final long fileSizeBytes;
    private final long lastOpenedTimestamp;
    private final int lastPageRead;
    private final boolean isFavorite;

    public RecentDocument(
            @NonNull String uri,
            String title,
            int pageCount,
            long fileSizeBytes,
            long lastOpenedTimestamp,
            int lastPageRead,
            boolean isFavorite
    ) {
        this.uri = Objects.requireNonNull(uri, "uri cannot be null");
        this.title = title != null ? title : "";
        this.pageCount = pageCount;
        this.fileSizeBytes = fileSizeBytes;
        this.lastOpenedTimestamp = lastOpenedTimestamp;
        this.lastPageRead = lastPageRead;
        this.isFavorite = isFavorite;
    }

    @NonNull
    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public int getPageCount() {
        return pageCount;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public long getLastOpenedTimestamp() {
        return lastOpenedTimestamp;
    }

    public int getLastPageRead() {
        return lastPageRead;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public RecentDocument copyWith(
            String title,
            Integer pageCount,
            Long fileSizeBytes,
            Long lastOpenedTimestamp,
            Integer lastPageRead,
            Boolean isFavorite
    ) {
        return new RecentDocument(
                this.uri,
                title != null ? title : this.title,
                pageCount != null ? pageCount : this.pageCount,
                fileSizeBytes != null ? fileSizeBytes : this.fileSizeBytes,
                lastOpenedTimestamp != null ? lastOpenedTimestamp : this.lastOpenedTimestamp,
                lastPageRead != null ? lastPageRead : this.lastPageRead,
                isFavorite != null ? isFavorite : this.isFavorite
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecentDocument that = (RecentDocument) o;
        return pageCount == that.pageCount &&
                fileSizeBytes == that.fileSizeBytes &&
                lastOpenedTimestamp == that.lastOpenedTimestamp &&
                lastPageRead == that.lastPageRead &&
                isFavorite == that.isFavorite &&
                uri.equals(that.uri) &&
                Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, title, pageCount, fileSizeBytes, lastOpenedTimestamp, lastPageRead, isFavorite);
    }
}
