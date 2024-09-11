# File Organizer

Copies files from one place to another, organizing by file type, year, and performing a de-dupe based on md5 hash.

Handles large sets of files, optimizes creating the target directories to minimize file IO.

Complete file list must be able to be stored in memory.

It has some optimizations for being run multiple times as some of my drives/adapters like to have IO issues or have drivers that cause the system to lockup... 
