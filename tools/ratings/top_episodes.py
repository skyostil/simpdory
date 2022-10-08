#!/usr/bin/python
import csv
import gzip

title_id = "tt0096697"  # The Simpsons
title_rating = {}

with gzip.open("title.ratings.tsv.gz", "rt") as fd:
  rd = csv.reader(fd, delimiter="\t", quotechar='"')
  next(rd)
  for row in rd:
    title_rating[row[0]] = float(row[1])

episode_ratings = []
with gzip.open("title.episode.tsv.gz", "rt") as fd:
  rd = csv.reader(fd, delimiter="\t", quotechar='"')
  next(rd)
  for row in rd:
    tid, parent_tid, season, episode = row
    if parent_tid == title_id and tid in title_rating:
      episode_ratings.append([title_rating[tid], int(season), int(episode)])

for rating, season, episode in sorted(episode_ratings, reverse=True):
  print("S%02dE%02d %.1f" % (season, episode, rating))