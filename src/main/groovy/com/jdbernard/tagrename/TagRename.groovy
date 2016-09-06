package com.jdbernard.tagrename

import java.util.ArrayList
import java.util.LinkedList

import org.docopt.Docopt
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag as JATag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import groovy.text.SimpleTemplateEngine

import static org.jaudiotagger.tag.FieldKey.*

public class TagRename {

  public static final String VERSION = "1.0.0"

  public static final String DOC = """\
tag-rename v$VERSION

Usage:
  tag-rename [options] <in-file>...

Options:
  -t --template <template>

    Use <template> as the basis for renaming files. The default template is
    '[%a]-[%t]'. Identifiers that can be used in the template are:

      %a - artist name
      %c - comment
      %t - song title
      %T - album title
      %d - disc number
      %n - track number
      %N - total number of tracks
      %y - album year
"""

  private static Logger logger = LoggerFactory.getLogger(TagRename)

  public static void main(String[] args) {

    def opts = new Docopt(DOC).withVersion("wdiwtlt v$VERSION").parse(args)


      String template = opts['--template'] ?: '[%a]-[%t]'

      List<File> files = opts['<in-file>'].collect { new File(it) }

      template = template.replaceAll(/\[%([^\]]+)\]/, '\\${$1}')
      renameFiles(files, template)
  }

  public static List<String> renameFiles(List<File> givenFiles, String template) {
    def engine = new SimpleTemplateEngine()
    def compiledTemplate = engine.createTemplate(template)
    List<String> renamed = new ArrayList<>()

    LinkedList<File> files = new LinkedList<>(givenFiles);

    while (files.size() > 0) {
      File file = files.poll()
      if (!file.exists()) {
        logger.error("Cannot find file: {}", file.canonicalPath)
        continue }

      if (file.isDirectory()) {
        files.addAll(file.listFiles())
        continue }

      def af, e
      try { af = AudioFileIO.read(file) }
      catch (Exception ex) { af = null; e = ex }

      if (!af || !af.tag)  {
        logger.info("Ignoring a file because I can't read the media tag " +
          "info:\n\t{}\n\t{}", file.canonicalPath, e.localizedMessage)
        continue }

      def binding = [
        a: af.tag.getAll(ARTIST)?.collect { it.trim() }?.join('/') ?: 'Unknown Artist',
        c: af.tag.getFirst(COMMENT)?.trim() ?: '',
        t: af.tag.getFirst(TITLE)?.trim() ?: 'Unknown Title',
        T: af.tag.getAll(ALBUM)?.collect {it.trim() }?.join('/') ?: '',
        d: af.tag.getFirst(DISC_NO)?.trim() ?: '',
        n: af.tag.getFirst(TRACK)?.trim() ?: '',
        N: af.tag.getFirst(TRACK_TOTAL)?.trim() ?: '',
        y: af.tag.getFirst(YEAR)?.trim() ?: ''
      ]

      String filename = compiledTemplate.make(binding).toString();
      def m = (file.name =~ /(\.[^\s\.]+)$/)
      String ext = m ? m[0][1] : ''

      String newPath = "${file.parentFile.canonicalPath}/${filename}${ext}"
      file.renameTo(newPath)
      renamed << newPath
      logger.info(newPath)
    }

    return renamed
  }

  private static void err(String msg, Exception ex = null) {
    logger.error(msg, ex)
    System.err.println("tag-rename: $msg")
    if (ex) System.err.println("\t${ex.localizedMessage}")
  }

  private static void exitErr(String msg, Exception ex = null) {
    logger.error(msg, ex)
    System.err.println("tag-rename: $msg")
    System.exit(1) }


}
