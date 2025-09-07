---
title: Transfer der Export-Verzeichnisse
identifier: intranda_administration_goobi2goobi_export intranda_administration_goobi2goobi_import_infrastructure intranda_administration_goobi2goobi_import_data
published: true
keywords:
    - Plugin
    - Administration Plugin
    - Transfer
---
Nachdem die Erzeugung der Export-Verzeichnisse durchgeführt wurde, können die Vorgangsordner vom Ausgangssystem zu Zielsystem kopiert werden. Je nachdem, um welche Datenmengen es sich hierbei handelt können verschiedene Methoden für den Transfer zum Einsatz kommen.

## Transfer mittels einer externen Festplatte

Soll für den Transfer eine externe Festplatte zum Einsatz kommen, kann mittels des `cp`-Befehls der Kopiervorgang vom Ausgangssysetm auf die Festplatte und später wieder von der Festplatte auf das Zielsystem erfolgen.

Beispielaufruf für den Kopiervorgang vom Ausgangssystem auf die externe Festplatte:

```bash
cp -r /opt/digiverso/goobi/metadata/* /external_harddisk/export/
```

Beispielsaufruf für den Kopiervorgang von der externen Festplatte auf das Zielsystem:

```bash
cp -r /external_harddisk/export/* /opt/digiverso/TEMP/
```

## Transfer über das Internet

Wenn zwischen dem Ausgangssystem und dem Zielsystem eine Netzwerkverbinung hergestellt werden kann, ist ein Datentransfer über die Kommandos `scp` oder `rsync` möglich. Der Vorteil des Transfers mittels `rsync` besteht dabei darin, dass eine gegebenenfalls auftretende Unterbrechung der Verbindung wieder aufgenommen werden kann, ohne den gesamten Transfer wieder von vorn beginnen zu müssen.

Beispielhaft sieht ein solcher Aufruf folgendermassen aus:

```bash
rsync -avhP --stats /opt/digiverso/goobi/metadata/ ZIELSYSTEM:/opt/digiverso/TEMP/
```

Soll der Aufruf nur bestimmte Verzeichnisse übertragen, eine maximale Bandbreite nutzen und auch andere Daten ausschließen, könnte ein solcher Aufruf auch etwas umfangreicher werden:

```bash
rsync -avhP --stats --bwlimit=10000 --exclude 'taskmanager' --exclude '*.xml.*' /opt/digiverso/goobi/metadata/{1,2,3,4,5,6,7,8,9,10} ZIELSYSTEM:/opt/digiverso/TEMP/
```

## Transfer in ein S3 Bucket eines AWS-Systems

Zum Export in ein S3 Bucket nach AWS kann das Skript `s3sync.py` verwendet werden.

