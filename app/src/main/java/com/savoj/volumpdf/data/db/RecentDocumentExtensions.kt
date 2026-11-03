package com.savoj.volumpdf.data.db

fun RecentDocument.copy(
    uri: String = this.uri,
    title: String = this.title,
    pageCount: Int = this.pageCount,
    fileSizeBytes: Long = this.fileSizeBytes,
    lastOpenedTimestamp: Long = this.lastOpenedTimestamp,
    lastPageRead: Int = this.lastPageRead,
    isFavorite: Boolean = this.isFavorite
): RecentDocument {
    return RecentDocument(
        uri,
        title,
        pageCount,
        fileSizeBytes,
        lastOpenedTimestamp,
        lastPageRead,
        isFavorite
    )
}
