package dev.jbiffis.caddie.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSyncTest {

    @Test
    fun protobufPrimitivesRoundTrip() {
        val bytes = Protobuf.Writer()
            .uint32(1, 300)
            .fixed64(2, 0xA5A5L)
            .bytes(3, "hi".toByteArray())
            .toByteArray()
        val fields = Protobuf.decode(bytes)
        assertEquals(300L, Protobuf.firstVarint(fields, 1))
        assertEquals(0xA5A5L, fields.first { it.number == 2 }.varint)
        assertEquals("hi", Protobuf.firstBytes(fields, 3)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun fileListRequestIsWellFormed() {
        val smart = FileSync.buildFileListRequest(startPageId = 33737)
        // Smart{43: FileSyncService{9: FileListRequest{2:pageId, 4:FileId, 5:FileId}}}
        val fss = FileSync.fileSyncServiceOf(smart)
        assertNotNull(fss)
        val req = Protobuf.firstBytes(Protobuf.decode(fss!!), FileSync.FS_FILE_LIST_REQUEST)
        assertNotNull(req)
        val f = Protobuf.decode(req!!)
        assertEquals(33737L, Protobuf.firstVarint(f, 2))
        // flags1 (field 4) is a FileId with id1/id2 = 0xa5a5
        val flags1 = Protobuf.firstBytes(f, 4)!!
        val fid = Protobuf.decode(flags1)
        assertEquals(0xA5A5L, fid.first { it.number == 1 }.varint)
    }

    @Test
    fun parsesFileListResponse() {
        // Hand-build Smart{43: FileSyncService{10: FileListResponse{3:nextPage,
        //   4:File{1:FileId, 2:FileType{2:name,3:code}, 3:size, 5:pageId}}}}
        val fileId = Protobuf.Writer().fixed64(1, 111).fixed64(2, 222)
        val fileType = Protobuf.Writer().bytes(2, "golf_scorecard".toByteArray()).uint32(3, 9)
        val file = Protobuf.Writer()
            .message(1, fileId).message(2, fileType).uint32(3, 3268).uint32(5, 33652)
        val listResp = Protobuf.Writer().uint32(3, 33738).message(4, file)
        val fss = Protobuf.Writer().message(FileSync.FS_FILE_LIST_RESPONSE, listResp)
        val smart = Protobuf.Writer().message(FileSync.SMART_FILE_SYNC, fss).toByteArray()

        val list = FileSync.parseFileListResponse(FileSync.fileSyncServiceOf(smart)!!)!!
        assertEquals(33738, list.nextPageId)
        assertEquals(1, list.files.size)
        val f = list.files[0]
        assertEquals("golf_scorecard", f.typeName)
        assertEquals(9, f.typeCode)
        assertEquals(3268L, f.size)
        assertEquals(33652, f.pageId)
        assertEquals(111L, f.id?.id1)
        assertEquals(222L, f.id?.id2)
    }

    @Test
    fun fileRequestEmbedsTheFile() {
        val file = FileSync.RemoteFile(FileSync.FileId(1, 2), "activity", 9, 100, 33652)
        val smart = FileSync.buildFileRequest(file)
        val fss = FileSync.fileSyncServiceOf(smart)!!
        val req = Protobuf.firstBytes(Protobuf.decode(fss), FileSync.FS_FILE_REQUEST)
        assertNotNull(req)
        // File is embedded at field 1
        val embedded = Protobuf.firstBytes(Protobuf.decode(req!!), 1)
        assertNotNull(embedded)
    }

    @Test
    fun fileResponseHandleParsedOnlyOnSuccess() {
        val ok = Protobuf.Writer().uint32(1, 0).uint32(3, 42) // status 0, handle 42
        val okSmart = Protobuf.Writer().message(
            FileSync.SMART_FILE_SYNC, Protobuf.Writer().message(FileSync.FS_FILE_RESPONSE, ok)
        ).toByteArray()
        assertEquals(42, FileSync.parseFileResponseHandle(FileSync.fileSyncServiceOf(okSmart)!!))

        val fail = Protobuf.Writer().uint32(1, 3).uint32(3, 42) // status 3 = failure
        val failSmart = Protobuf.Writer().message(
            FileSync.SMART_FILE_SYNC, Protobuf.Writer().message(FileSync.FS_FILE_RESPONSE, fail)
        ).toByteArray()
        assertNull(FileSync.parseFileResponseHandle(FileSync.fileSyncServiceOf(failSmart)!!))
    }
}
