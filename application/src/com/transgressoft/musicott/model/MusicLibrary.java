/*
 * This file is part of Musicott software.
 *
 * Musicott software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Musicott library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Musicott. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2015 - 2017 Octavio Calleya
 */

package com.transgressoft.musicott.model;

import com.google.common.collect.*;
import com.google.inject.*;
import com.transgressoft.musicott.player.*;
import com.transgressoft.musicott.util.guice.annotations.*;
import com.transgressoft.musicott.view.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.control.Alert.*;
import org.slf4j.*;

import java.util.*;
import java.util.Map.*;

import static com.transgressoft.musicott.util.Utils.*;

/**
 * Singleton class that manages some operations over the
 * collection of tracksLibrary, waveformsLibrary, playlistsLibrary, artistsLibrary and albumsLibrary.
 *
 * @author Octavio Calleya
 * @version 0.10.1-b
 */
@Singleton
public class MusicLibrary {

    private final Logger LOG = LoggerFactory.getLogger(getClass().getName());

    private final PlayerFacade playerFacade;

    private TracksLibrary tracksLibrary;
    private ArtistsLibrary artistsLibrary;
    private AlbumsLibrary albumsLibrary;
    private WaveformsLibrary waveformsLibrary;
    private PlaylistsLibrary playlistsLibrary;

    private RootController rootController;
    private NavigationController navigationController;
    private SimpleProgressBarController progressBarController;

    @Inject
    public MusicLibrary(PlayerFacade playerFacade) {
        this.playerFacade = playerFacade;
    }

    public void showArtist(String artist) {
        showArtistAndSelectTrack(artist, null);
    }

    public void showArtistAndSelectTrack(String artist, Entry<Integer, Track> trackToSelect) {
        new Thread(() -> {
            Multimap<String, Entry<Integer, Track>> newTrackSetsByAlbum = getAlbumTracksOfArtist(artist);
            Platform.runLater(() -> {
                rootController.setArtistTrackSets(newTrackSetsByAlbum);
                if (trackToSelect != null)
                    rootController.selectTrack(trackToSelect);
            });
        }).start();
    }

    public void deleteTracks(Collection<Track> trackSelection) {
        if (! trackSelection.isEmpty()) {
            javafx.stage.Stage mainStage = rootController.getStage();
            String alertHeader = "Delete " + trackSelection.size() + " files from Musicott?";
            Alert alert = createAlert("", alertHeader, "", AlertType.CONFIRMATION, mainStage);
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get().getButtonData().isDefaultButton())
                deleteTracksFromLibraries(trackSelection);
            else
                alert.close();
        }
    }

    private void deleteTracksFromLibraries(Collection<Track> trackSelection) {
        new Thread(() -> {
            playerFacade.deleteFromQueues(trackSelection);
            if (trackSelection.size() == tracksLibrary.getSize())
                Platform.runLater(this::clearCollections);
            else {
                tracksLibrary.remove(trackSelection);
                playlistsLibrary.removeFromPlaylists(trackSelection);
                waveformsLibrary.removeWaveforms(trackSelection);
                LOG.debug("Deleted {} tracks from tracks, playlists and waveforms libraries", trackSelection.size());
            }

            String message = "Deleted " + Integer.toString(trackSelection.size()) + " tracks";
            Platform.runLater(() -> {
                navigationController.setStatusMessage(message);
                progressBarController.getStage().hide();
            });
        }).start();
        progressBarController.getStage().show();
    }

    private void clearCollections() {
        tracksLibrary.clear();
        artistsLibrary.clear();
        albumsLibrary.clear();
        waveformsLibrary.clearWaveforms();
        playlistsLibrary.clearPlaylists();
        LOG.info("Cleared tracks, artists, albums, waveforms and playlists");
    }

    /**
     * Searches and returns all the tracks, mapped by album, in which an artist is involved.
     *
     * @param artist The given artist to find their related tracks
     * @return A {@link ImmutableMultimap} with the albums as keys, and {@code Entries} as values
     */
    public ImmutableMultimap<String, Entry<Integer, Track>> getAlbumTracksOfArtist(String artist) {
        ImmutableMultimap<String, Entry<Integer, Track>> artistAlbumTracks;
        if (artistsLibrary.contains(artist)) {
            Set<String> artistAlbums = artistsLibrary.getArtistAlbums(artist);
            artistAlbumTracks = albumsLibrary.getTracksByAlbum(artist, artistAlbums);
        }
        else
            artistAlbumTracks = ImmutableMultimap.of();
        return artistAlbumTracks;
    }

    /**
     * Makes a random playlist of tracksLibrary of the given artist and adds it to the {@link PlayerFacade}
     *
     * @param artist The given artist
     */
    public void playRandomArtistPlaylist(String artist) {
        Thread randomArtistPlaylistThread = new Thread(() -> {
            List<Track> randomList = artistsLibrary.getRandomListOfArtistTracks(artist);
            playerFacade.setRandomListAndPlay(randomList);
            LOG.debug("Created random playlist from artist");
        }, "Random Artist Playlist Thread");
        randomArtistPlaylistThread.start();
    }

    public void playPlaylistRandomly(Playlist playlist) {
        Thread randomPlaylistPlayThread = new Thread(() -> {
            List<Track> randomList = tracksLibrary.getRandomSortedTrackList(playlist);
            playerFacade.setRandomListAndPlay(randomList);
            LOG.debug("Created random playlist from playlist");
        }, "Shuffle Playlist Thread");
        randomPlaylistPlayThread.start();
    }

    @Inject (optional = true)
    public void setNavigationController(@NavigationCtrl NavigationController c) {
        navigationController = c;
    }

    @Inject (optional = true)
    public void setRootController(@RootCtrl RootController c) {
        rootController = c;
    }

    @Inject
    public void setProgressBarController(SimpleProgressBarController progressBarController) {
        this.progressBarController = progressBarController;
    }

    @Inject
    public void setTracksLibrary(TracksLibrary tracksLibrary) {
        this.tracksLibrary = tracksLibrary;
    }

    @Inject
    public void setArtistsLibrary(ArtistsLibrary artistsLibrary) {
        this.artistsLibrary = artistsLibrary;
    }

    @Inject
    public void setAlbumsLibrary(AlbumsLibrary albumsLibrary) {
        this.albumsLibrary = albumsLibrary;
    }

    @Inject
    public void setWaveformsLibrary(WaveformsLibrary waveformsLibrary) {
        this.waveformsLibrary = waveformsLibrary;
    }

    @Inject
    public void setPlaylistsLibrary(PlaylistsLibrary playlistsLibrary) {
        this.playlistsLibrary = playlistsLibrary;
    }
}
