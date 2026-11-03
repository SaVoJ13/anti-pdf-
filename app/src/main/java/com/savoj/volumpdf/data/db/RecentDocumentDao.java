package com.savoj.volumpdf.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface RecentDocumentDao {

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedTimestamp DESC")
    List<RecentDocument> getAllRecent();

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    List<RecentDocument> getFavorites();

    @Query("SELECT * FROM recent_documents WHERE uri = :uri LIMIT 1")
    RecentDocument getByUri(String uri);

    @Query("SELECT * FROM recent_documents WHERE title LIKE '%' || :query || '%' ORDER BY lastOpenedTimestamp DESC")
    List<RecentDocument> searchDocuments(String query);

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 AND title LIKE '%' || :query || '%' ORDER BY lastOpenedTimestamp DESC")
    List<RecentDocument> searchFavorites(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RecentDocument document);

    @Update
    void update(RecentDocument document);

    @Delete
    void delete(RecentDocument document);

    @Query("DELETE FROM recent_documents WHERE uri = :uri")
    void deleteByUri(String uri);

    @Query("UPDATE recent_documents SET isFavorite = :isFavorite WHERE uri = :uri")
    void setFavorite(String uri, boolean isFavorite);

    @Query("UPDATE recent_documents SET lastOpenedTimestamp = :timestamp, lastPageRead = :page WHERE uri = :uri")
    void updateReadingProgress(String uri, int page, long timestamp);

    @Query("DELETE FROM recent_documents")
    void clearAll();
}
