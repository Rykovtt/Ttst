package com.kartoteka.app.data

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import java.io.File

/** Coil: зашифрованные фото архива (`*.enc`) расшифровываются в память, на диск ничего не пишется. */
class EncryptedPhotoFetcher(private val file: File, private val options: Options, private val photos: PhotoStorage) : Fetcher {
    override suspend fun fetch(): FetchResult = SourceResult(
        source = ImageSource(Buffer().write(photos.readBytes(file.absolutePath)), options.context),
        mimeType = "image/jpeg",
        dataSource = DataSource.DISK,
    )

    class Factory(private val photos: () -> PhotoStorage) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.name.endsWith(PhotoStorage.ENC)) EncryptedPhotoFetcher(data, options, photos()) else null
    }
}
