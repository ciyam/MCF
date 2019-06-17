#!/usr/bin/env perl

use POSIX;

open(PROPS, '<', 'target/classes/build.properties') || die("Can't open target/classes/build.properties: $!\n");
while (<PROPS>) {
	if (m/build.timestamp=(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})Z/) {
		$timestamp = strftime('%s', $6, $5, $4, $3, $2 - 1, $1 - 1900, 0, 0, 0);
		last;
	}
}
close(PROPS);

die("Can't process build.timestamp\n") if ! defined $timestamp;

$commit_hash = `git show --no-patch --format=%H`;

die("Can't find commit hash\n") if ! defined $commit_hash;
chomp $commit_hash;

$sha256sum = `sha256sum MCF-core.update 2>/dev/null || sha256 MCF-core.update 2>/dev/null`;

die("Can't calculate SHA256 of MCF-core.update\n") unless $sha256sum =~ m/(\S{64})/;

$sha256 = $1;

printf "timestamp (ms): %016x\n", $timestamp;
printf "commit hash: %s\n", $commit_hash;
printf "SHA256 of MCF-core.update: %s\n", $sha256;

$data = sprintf "%016x%s%s", $timestamp * 1000, $commit_hash, $sha256;
printf "data payload: %s\n", $data;

$tx_type = 10;
$tx_timestamp = time() * 1000;
$tx_group_id = 1;
$service = 1;
printf "ARBITRARY(%d) transaction with timestamp %d, txGroupID %d and service %d\n", $tx_type, $tx_timestamp, $tx_group_id, $service;

$n_payments = 0;
$data_length = length($data) / 2;
$fee = 0.1 * 1e8;
printf "%08x%016x%08x%s%s%08x%08x%08x%s%016x%s\n", $tx_type, $tx_timestamp, $tx_group_id, '[reference]', '[public key]', $n_payments, $service, $data_length, $data, $fee, '[signature]';
