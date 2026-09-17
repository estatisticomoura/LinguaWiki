package org.linguawiki.offline.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.Closeable
import java.io.File

class OfflinePackDatabase(file: File, private val descriptor: OfflinePackDescriptor) : Closeable {
    private val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun search(rawQuery: String, limit: Int = 30): List<EntrySummary> {
        val query = SearchNormalizer.canonical(rawQuery)
        if (query.isBlank()) return emptyList()
        val folded = SearchNormalizer.fold(query)
        val matches = linkedMapOf<String, RankedSummary>()
        addEntryMatches("e.lemma_key = ?", arrayOf(query), MatchKind.EXACT_LEMMA, 0, matches)
        addFormMatches("f.surface_key = ?", arrayOf(query), MatchKind.EXACT_FORM, 1, matches)
        addEntryMatches("e.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 2, matches)
        addFormMatches("f.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 3, matches)
        val prefix = escapeLike(folded) + "%"
        addEntryMatches("e.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 4, matches)
        addFormMatches("f.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 5, matches)
        if (matches.size < 8 && folded.length >= 3) {
            val threshold = if (folded.length >= 8) 3 else 2
            database.rawQuery("SELECT e.stable_id,e.lemma,e.part_of_speech,e.ipa,e.folded_key,e.content_score FROM entries e WHERE ABS(LENGTH(e.folded_key)-?)<=? ORDER BY e.content_score DESC LIMIT 1000", arrayOf(folded.length.toString(), threshold.toString())).use { c ->
                while (c.moveToNext()) if (SearchNormalizer.levenshtein(folded, c.getString(4)) <= threshold) putRanked(matches, c.toSummary(MatchKind.APPROXIMATE, scoreIndex = 5), 10)
            }
        }
        return matches.values.groupBy { SearchNormalizer.canonical(it.summary.lemma) }.values.map { group ->
            val ordered = group.sortedWith(compareBy<RankedSummary> { it.rank }.thenByDescending { it.summary.contentScore })
            val primary = ordered.first().summary
            primary.copy(entryIds = ordered.map { it.summary.id }.distinct(), partsOfSpeech = ordered.map { it.summary.partOfSpeech }.distinct(), contentScore = ordered.sumOf { it.summary.contentScore })
        }.sortedWith(compareBy<EntrySummary> { matches[it.entryIds.first()]?.rank ?: 99 }.thenByDescending { it.contentScore }.thenBy { it.lemma }).take(limit)
    }

    fun findExact(lemma: String): String? = database.rawQuery("SELECT stable_id FROM entries WHERE lemma_key=? ORDER BY content_score DESC,id LIMIT 1", arrayOf(SearchNormalizer.canonical(lemma))).use { if (it.moveToFirst()) externalId(it.getString(0)) else null }
    fun hasEntry(entryId: String) = stableId(entryId)?.let { id -> database.rawQuery("SELECT 1 FROM entries WHERE stable_id=?", arrayOf(id)).use { it.moveToFirst() } } ?: false
    fun siblingEntryIds(entryId: String): List<String> {
        val stable = stableId(entryId) ?: return emptyList()
        return database.rawQuery("SELECT stable_id FROM entries WHERE lemma_key=(SELECT lemma_key FROM entries WHERE stable_id=?) ORDER BY content_score DESC,id", arrayOf(stable)).use { c -> buildList { while (c.moveToNext()) add(externalId(c.getString(0))) } }
    }

    fun getEntry(entryId: String): DictionaryEntry? {
        val stable = stableId(entryId) ?: return null
        val base = database.rawQuery("SELECT id,stable_id,lemma,part_of_speech,ipa,etymology,inflection_kind,grammatical_features_json FROM entries WHERE stable_id=?", arrayOf(stable)).use { c ->
            if (!c.moveToFirst()) return null
            EntryBase(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.nullableString(4),c.nullableString(5),c.nullableString(6),parseArray(c.nullableString(7)))
        }
        val translations = mutableListOf<Translation>()
        database.rawQuery("SELECT sense_order,sense_label,language_code,language_name,term,tags_json FROM translations WHERE entry_id=? ORDER BY sense_order,language_name,term", arrayOf(base.internalId.toString())).use { c ->
            while(c.moveToNext()) translations += Translation(c.getString(2),c.getString(3),c.getString(4),c.getString(4),null,if(c.isNull(0)) null else c.getInt(0),c.nullableString(1),parseArray(c.nullableString(5)))
        }
        val senses = mutableListOf<Sense>()
        database.rawQuery("SELECT sense_order,definition,examples_json FROM senses WHERE entry_id=? ORDER BY sense_order", arrayOf(base.internalId.toString())).use { c -> while(c.moveToNext()) { val order=c.getInt(0); senses += Sense(order,c.getString(1),parseArray(c.nullableString(2)),translations.filter { it.senseOrder==order }) } }
        val forms = mutableListOf<WordForm>()
        database.rawQuery("SELECT surface,label,tags_json,raw_tags_json FROM forms WHERE entry_id=? ORDER BY id", arrayOf(base.internalId.toString())).use { c -> while(c.moveToNext()) forms += WordForm(c.getString(0),c.getString(1),parseArray(c.nullableString(2)),parseArray(c.nullableString(3))) }
        val pronunciations = mutableListOf<Pronunciation>()
        database.rawQuery("SELECT ipa,labels_json FROM pronunciations WHERE entry_id=? ORDER BY id", arrayOf(base.internalId.toString())).use { c -> while(c.moveToNext()) pronunciations += Pronunciation(c.getString(0),parseArray(c.nullableString(1))) }
        return DictionaryEntry(externalId(base.stableId),descriptor.primaryEdition,descriptor.headwordLanguage,base.lemma,base.partOfSpeech,base.ipa,pronunciations.ifEmpty { base.ipa?.let { listOf(Pronunciation(it)) }.orEmpty() },base.etymology,base.inflectionKind,base.features,senses,forms,translations.filter { it.senseOrder==null },false)
    }

    override fun close()=database.close()
    private fun addEntryMatches(condition:String,args:Array<String>,kind:MatchKind,rank:Int,target:MutableMap<String,RankedSummary>) { database.rawQuery("SELECT e.stable_id,e.lemma,e.part_of_speech,e.ipa,e.content_score FROM entries e WHERE " + condition + " ORDER BY e.content_score DESC,e.lemma LIMIT 50",args).use { c -> while(c.moveToNext()) putRanked(target,c.toSummary(kind),rank) } }
    private fun addFormMatches(condition:String,args:Array<String>,kind:MatchKind,rank:Int,target:MutableMap<String,RankedSummary>) { database.rawQuery("SELECT e.stable_id,e.lemma,e.part_of_speech,e.ipa,e.content_score,f.surface FROM forms f JOIN entries e ON e.id=f.entry_id WHERE " + condition + " ORDER BY e.content_score DESC,e.lemma LIMIT 50",args).use { c -> while(c.moveToNext()) putRanked(target,c.toSummary(kind,c.getString(5)),rank) } }
    private fun Cursor.toSummary(kind:MatchKind,surface:String?=null,scoreIndex:Int=4)=EntrySummary(externalId(getString(0)),descriptor.primaryEdition,descriptor.headwordLanguage,getString(1),getString(2),nullableString(3),surface,kind,false,contentScore=getLong(scoreIndex))
    private fun putRanked(target:MutableMap<String,RankedSummary>,summary:EntrySummary,rank:Int){ val old=target[summary.id]; if(old==null||rank<old.rank) target[summary.id]=RankedSummary(summary,rank) }
    private fun parseArray(json:String?):List<String>{ if(json.isNullOrBlank()) return emptyList(); return runCatching { val a=JSONArray(json); buildList { for(i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let(::add) } }.getOrDefault(emptyList()) }
    private fun externalId(stable:String)="pack/" + descriptor.packId + "/" + stable
    private fun stableId(id:String)=id.takeIf { it.startsWith("pack/" + descriptor.packId + "/") }?.substringAfterLast('/')
    private fun Cursor.nullableString(i:Int)=if(isNull(i)) null else getString(i)
    private fun escapeLike(v:String)=v.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")
    private data class RankedSummary(val summary:EntrySummary,val rank:Int)
    private data class EntryBase(val internalId:Long,val stableId:String,val lemma:String,val partOfSpeech:String,val ipa:String?,val etymology:String?,val inflectionKind:String?,val features:List<String>)
}
