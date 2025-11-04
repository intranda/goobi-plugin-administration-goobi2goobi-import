---
title: Transfer of the export directories
identifier: intranda_administration_goobi2goobi_export intranda_administration_goobi2goobi_import_infrastructure intranda_administration_goobi2goobi_import_data
published: true
keywords:
    - Goobi workflow
    - Plugin
    - Administration Plugin
---
After the export directories have been created, the task folders can be copied from the source system to the target system. Depending on the amount of data involved, different methods can be used for the transfer.

## Transfer using an external hard disk

If an external hard disk is to be used for the transfer, the `cp` command can be used to copy from the source system to the hard disk and later back from the hard disk to the target system.

Example call for the copy operation from the source system to the external hard disk:

```bash
cp -r /opt/digiverso/goobi/metadata/* /external_harddisk/export/
```

Example call for the copy operation from the external hard disk to the target system:

```bash
cp -r /external_harddisk/export/* /opt/digiverso/TEMP/
```

## Transfer via the Internet

If a network connection can be established between the source system and the target system, data transfer is possible using the commands `scp` or `rsync`. The advantage of transferring data using `rsync` is that any interruption of the connection can be resumed without having to start the entire transfer again from the beginning.

An example of such a call is as follows:

```bash
rsync -avhP --stats /opt/digiverso/goobi/metadata/ ZIELSYSTEM:/opt/digiverso/TEMP/
```

If the call should only transfer certain directories, use a maximum bandwidth and also exclude other data, such a call could also become a bit more extensive:

```bash
rsync -avhP --stats --bwlimit=10000 --exclude 'taskmanager' --exclude '*.xml.*' /opt/digiverso/goobi/metadata/{1,2,3,4,5,6,7,8,9,10} ZIELSYSTEM:/opt/digiverso/TEMP/
```

## Transfer to an S3 Bucket of an AWS system

To export to an S3 Bucket to AWS you can use the script `s3sync.py`.

